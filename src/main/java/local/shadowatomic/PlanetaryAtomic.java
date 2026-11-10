package local.shadowatomic;

import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Server-authoritative selective strike. Macro planets never alter Minecraft coordinates. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID)
public final class PlanetaryAtomic {
    public static final double VOICE_AT=8.4,IMPACT_AT=13.4,END_AT=20;
    public static final double RANGE=256;
    private static final Map<UUID,State> ACTIVE=new HashMap<>();
    private static final Map<UUID,Long> COOLDOWN=new HashMap<>();
    public static UUID lastEvent;
    private static final class State {
        final ServerLevel level;final UUID owner,event=UUID.randomUUID(),target;final int caster,targetId;
        final Vec3 origin;final AtomicMode mode;final long started=AtomicClock.now();
        final Set<UUID> seen=new HashSet<>();boolean committed;
        Vec3 targetPos;final String targetName;
        final AtomicNetwork.PlanetLook look;
        State(ServerPlayer p,LivingEntity t,AtomicMode mode){level=p.serverLevel();owner=p.getUUID();caster=p.getId();origin=p.position();this.mode=mode;target=t==null?null:t.getUUID();targetId=t==null?-1:t.getId();targetPos=t==null?origin.add(p.getLookAngle().scale(32)):t.position();String name=t==null?"":t.getDisplayName().getString();targetName=name.substring(0,Math.min(256,name.length()));look=AtomicNetwork.PlanetLook.capture(t);}
        double age(){return (AtomicClock.now()-started)/1e9;}
        AtomicNetwork.PlanetBegin packet(){return new AtomicNetwork.PlanetBegin(caster,event,origin,targetId,targetPos,targetName,mode,Math.max(0,(AtomicClock.now()-started)/1_000_000),look);}
    }
    public static boolean active(UUID owner){return ACTIVE.containsKey(owner);}
    public static boolean activeIn(ServerLevel level){return ACTIVE.values().stream().anyMatch(s->s.level==level);}
    public static UUID event(UUID owner){var s=ACTIVE.get(owner);return s==null?null:s.event;}
    public static boolean validTarget(ServerPlayer p,LivingEntity target){return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==p.level()&&!(target instanceof Player)&&!(target instanceof ArmorStand)&&!target.isAlliedTo(p)&&!(target instanceof TamableAnimal pet&&p.getUUID().equals(pet.getOwnerUUID()))&&p.distanceToSqr(target)<=RANGE*RANGE;}
    private static LivingEntity livingTarget(Entity entity){
        return entity instanceof EnderDragonPart part?part.parentMob:entity instanceof LivingEntity living?living:null;
    }
    private static LivingEntity aimed(ServerPlayer p){
        Vec3 eye=p.getEyePosition(),delta=p.getViewVector(1).scale(RANGE),end=eye.add(delta);
        var block=p.level().clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        double distance=block.getType()==HitResult.Type.MISS?RANGE*RANGE:eye.distanceToSqr(block.getLocation());
        var hit=ProjectileUtil.getEntityHitResult(p,eye,end,p.getBoundingBox().expandTowards(delta).inflate(1),e->validTarget(p,livingTarget(e)),distance);
        return hit==null?null:livingTarget(hit.getEntity());
    }
    public static void request(ServerPlayer player,boolean preview){
        if(player.isDeadOrDying()||player.isSpectator())return;
        if(preview&&!player.hasPermissions(2)&&!player.isCreative())return;
        if(!preview&&!player.getMainHandItem().is(AtomicMod.SWORD.get())&&!player.getOffhandItem().is(AtomicMod.SWORD.get()))return;
        long tick=player.server.getTickCount();UUID uuid=player.getUUID();
        if(ACTIVE.containsKey(uuid)||AtomicDomains.event(uuid)!=null||COOLDOWN.getOrDefault(uuid,0L)>tick||AtomicServer.busy(player.serverLevel())){player.displayClientMessage(Component.translatable("message.shadow_atomic.busy"),true);return;}
        AtomicMode mode=preview?AtomicMode.PREVIEW:AtomicConfig.mode();LivingEntity target=aimed(player);
        if(mode.damagesEntities()&&target==null){player.displayClientMessage(Component.translatable("message.shadow_atomic.planet_target"),true);return;}
        State state=new State(player,target,mode);ACTIVE.put(uuid,state);COOLDOWN.put(uuid,tick+AtomicConfig.PLANET_COOLDOWN.get());lastEvent=state.event;broadcast(state);
        AtomicMod.LOG.info("PLANETARY_BEGIN event={} caster={} target={} mode={} impactSeconds={} terrainProtected=true",state.event,uuid,state.target,mode,IMPACT_AT);
    }
    private static void broadcast(State state){for(var p:state.level.players())if(!p.isDeadOrDying()&&p.position().distanceToSqr(state.origin)<900*900){state.seen.add(p.getUUID());AtomicNetwork.planetBegin(p,state.packet());}}
    public static void cancel(ServerPlayer p,String reason){cancel(p.getUUID(),reason);}
    private static void cancel(UUID uuid,String reason){State s=ACTIVE.remove(uuid);if(s==null)return;end(s);AtomicMod.LOG.info("PLANETARY_CANCEL event={} reason={} committed={}",s.event,reason,s.committed);}
    private static void end(State s){for(var viewer:s.level.getServer().getPlayerList().getPlayers())if(s.seen.contains(viewer.getUUID()))AtomicNetwork.planetEnd(viewer,s.event);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent tick){
        if(tick.phase!=TickEvent.Phase.END)return;
        for(var s:new ArrayList<>(ACTIVE.values())){
            ServerPlayer caster=s.level.getServer().getPlayerList().getPlayer(s.owner);
            if(caster==null||caster.isDeadOrDying()||caster.serverLevel()!=s.level){cancel(s.owner,"caster_invalid");continue;}
            LivingEntity target=s.target==null?null:s.level.getEntity(s.target) instanceof LivingEntity living?living:null;
            if(!s.committed&&s.mode.damagesEntities()&&!validTarget(caster,target)){cancel(s.owner,"target_invalid");continue;}
            if(target!=null)s.targetPos=target.position();
            if(!s.committed&&s.age()>=IMPACT_AT){
                s.committed=true;
                try{if(s.mode.damagesEntities())AtomicServer.targetStrike(caster,s.event,s.origin,target);AtomicMod.LOG.info("PLANETARY_IMPACT event={} target={} mode={} terrainProtected=true",s.event,s.target,s.mode);}
                catch(Exception error){AtomicMod.LOG.error("PLANETARY_ABORT no unrecorded strike",error);cancel(s.owner,"journal_failure");continue;}
            }
            if(s.age()>=END_AT){ACTIVE.remove(s.owner);end(s);continue;}
            if(caster.server.getTickCount()%20==0){
                for(var viewer:s.level.getServer().getPlayerList().getPlayers()){
                    boolean visible=viewer.serverLevel()==s.level&&!viewer.isDeadOrDying()&&viewer.position().distanceToSqr(s.origin)<900*900;
                    if(visible){s.seen.add(viewer.getUUID());AtomicNetwork.planetBegin(viewer,s.packet());}
                    else if(s.seen.remove(viewer.getUUID()))AtomicNetwork.planetEnd(viewer,s.event);
                }
            }
        }
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server!=null&&server.getTickCount()%10==0)for(var p:server.getPlayerList().getPlayers())AtomicNetwork.planetStatus(p,new AtomicNetwork.PlanetStatus((int)Math.max(0,COOLDOWN.getOrDefault(p.getUUID(),0L)-server.getTickCount()),active(p.getUUID()),p.level().dimension().location()));
    }
    @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p,"death");}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){cancel((ServerPlayer)e.getEntity(),"logout");COOLDOWN.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){cancel((ServerPlayer)e.getEntity(),"dimension");}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){ACTIVE.clear();COOLDOWN.clear();lastEvent=null;}
}
