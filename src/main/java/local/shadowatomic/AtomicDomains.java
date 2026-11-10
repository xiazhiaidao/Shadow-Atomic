package local.shadowatomic;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Authoritative two-press lifecycle. A held domain has no expiry or damage timer. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID)
public final class AtomicDomains {
    public static final int RELEASE_TICKS=100;
    public static final long RELEASE_NANOS=5_000_000_000L;
    private static final Map<UUID,State> ACTIVE=new HashMap<>();
    private static final Map<UUID,Long> COOLDOWN=new HashMap<>(),INPUT_AFTER=new HashMap<>();
    private static final class State {
        final ServerLevel level;final UUID caster,event;final int entityId;int radius;Vec3 origin,lastSent;boolean impactLocked;
        final AtomicMode mode;final long started;final Set<UUID> seen=new HashSet<>();
        final long startedTime=AtomicClock.now();boolean releasing;long released,releasedTime;
        State(ServerPlayer player,AtomicMode mode,int radius){level=player.serverLevel();caster=player.getUUID();entityId=player.getId();event=UUID.randomUUID();origin=player.position();this.mode=mode;this.radius=radius;started=level.getGameTime();}
        AtomicNetwork.Begin packet(){return new AtomicNetwork.Begin(entityId,event,origin,releasing?released:started,radius/200f,mode,releasing,Math.max(0,(AtomicClock.now()-(releasing?releasedTime:startedTime))/1_000_000),impactLocked);}
    }
    public static boolean holding(UUID player){State s=ACTIVE.get(player);return s!=null&&!s.releasing;}
    public static UUID event(UUID player){State s=ACTIVE.get(player);return s==null?null:s.event;}
    private static Vec3 center(State s){
        if(!s.impactLocked){var p=s.level.getServer().getPlayerList().getPlayer(s.caster);if(p!=null&&p.serverLevel()==s.level)s.origin=p.position();}
        return s.origin;
    }
    public static Vec3 origin(UUID player){State s=ACTIVE.get(player);return s==null?null:center(s);}
    /** Freeze the single authoritative center before any Recovery journal or damage is written. */
    public static Vec3 lockImpact(ServerPlayer player,UUID event,Vec3 fallback){
        State s=ACTIVE.get(player.getUUID());if(s==null||!s.event.equals(event))return fallback;
        if(!s.impactLocked){center(s);s.impactLocked=true;broadcast(s);AtomicMod.LOG.info("ATOMIC_IMPACT_LOCK event={} origin={}",event,s.origin);}
        return s.origin;
    }
    public static boolean contains(LivingEntity entity){
        for(State s:ACTIVE.values())if(s.level==entity.level()&&entity.position().distanceToSqr(center(s))<=s.radius*(double)s.radius)return true;
        return false;
    }
    public static void resizeHeld(ServerPlayer p,int radius){State s=ACTIVE.get(p.getUUID());if(s!=null&&!s.releasing&&s.level==p.serverLevel()){s.radius=ShadowRanges.clamp(radius);DomainStasis.refresh();broadcast(s);}}
    public static AtomicNetwork.Status status(ServerPlayer player){
        long now=player.server.getTickCount();State state=ACTIVE.get(player.getUUID());
        int remaining=(int)Math.max(0,Math.min(300,COOLDOWN.getOrDefault(player.getUUID(),0L)-now));
        AtomicMode mode=state==null?AtomicConfig.mode():state.mode;
        AtomicNetwork.Phase phase=state!=null?(state.releasing?AtomicNetwork.Phase.RELEASING:AtomicNetwork.Phase.HOLDING):remaining>0?AtomicNetwork.Phase.COOLDOWN:mode.damagesEntities()&&AtomicServer.busy(player.serverLevel())?AtomicNetwork.Phase.WORLD_BUSY:AtomicNetwork.Phase.READY;
        return new AtomicNetwork.Status(now,remaining,mode,phase,player.level().dimension().location());
    }
    private static void syncStatus(ServerPlayer player){AtomicNetwork.status(player,status(player));}
    public static void toggle(ServerPlayer player,AtomicMode mode,int radius){
        if(player.isDeadOrDying()||player.isSpectator())return;
        if(PlanetaryAtomic.active(player.getUUID())||RecoveryAtomic.activeIn(player.serverLevel())){player.displayClientMessage(Component.translatable("message.shadow_atomic.busy"),true);return;}
        long now=player.server.getTickCount();UUID uuid=player.getUUID();
        if(INPUT_AFTER.getOrDefault(uuid,0L)>now)return;
        INPUT_AFTER.put(uuid,now+6);
        State state=ACTIVE.get(uuid);
        if(state!=null){
            if(state.level!=player.serverLevel()){cancel(player,"dimension");return;}
            if(state.releasing){player.displayClientMessage(Component.translatable("message.shadow_atomic.busy"),true);return;}
            if(state.mode.damagesEntities()&&AtomicServer.busy(state.level)){player.displayClientMessage(Component.translatable("message.shadow_atomic.terrain_busy"),true);return;}
            center(state);state.releasing=true;state.released=state.level.getGameTime();state.releasedTime=AtomicClock.now();COOLDOWN.put(uuid,now+300);
            if(state.mode.damagesEntities())AtomicServer.schedule(player,state.event,state.origin,state.radius,state.mode);
            broadcast(state);
            syncStatus(player);
            AtomicMod.LOG.info("ATOMIC_RELEASE event={} mode={} heldTicks={} fireDelayMillis={}",state.event,state.mode,state.released-state.started,RELEASE_NANOS/1_000_000);
            return;
        }
        if(COOLDOWN.getOrDefault(uuid,0L)>now){player.displayClientMessage(Component.translatable("message.shadow_atomic.busy"),true);return;}
        if(mode.damagesEntities()&&AtomicServer.busy(player.serverLevel())){player.displayClientMessage(Component.translatable("message.shadow_atomic.terrain_busy"),true);return;}
        state=new State(player,mode,Math.max(10,Math.min(384,radius)));ACTIVE.put(uuid,state);COOLDOWN.put(uuid,now+300);broadcast(state);
        syncStatus(player);
        AtomicMod.LOG.info("ATOMIC_DOMAIN_BEGIN event={} dimension={} origin={} mode={} radius={}",state.event,state.level.dimension().location(),state.origin,mode,state.radius);
    }
    public static void input(ServerPlayer player,boolean preview){
        if(preview){
            if(!player.isCreative()&&!player.hasPermissions(2))return;
            State active=ACTIVE.get(player.getUUID());if(active!=null&&active.mode!=AtomicMode.PREVIEW)return;
            toggle(player,AtomicMode.PREVIEW,200);
        }else{
            if(!player.getMainHandItem().is(AtomicMod.SWORD.get())&&!player.getOffhandItem().is(AtomicMod.SWORD.get()))return;
            toggle(player,AtomicConfig.mode(),ShadowRanges.get(player).attack());
        }
    }
    private static void broadcast(State state){
        state.lastSent=state.origin;
        for(ServerPlayer viewer:state.level.getServer().getPlayerList().getPlayers())if(!viewer.isDeadOrDying()&&viewer.serverLevel()==state.level&&(state.seen.contains(viewer.getUUID())||viewer.position().distanceToSqr(state.origin)<900*900)){state.seen.add(viewer.getUUID());AtomicNetwork.send(viewer,state.packet());}
    }
    public static void cancel(ServerPlayer player,String reason){
        State state=ACTIVE.remove(player.getUUID());if(state==null)return;
        DomainStasis.refresh();
        AtomicServer.cancelPending(player.getUUID());
        for(ServerPlayer viewer:state.level.getServer().getPlayerList().getPlayers())if(state.seen.contains(viewer.getUUID())||viewer==player)AtomicNetwork.end(viewer,state.event);
        syncStatus(player);
        AtomicMod.LOG.info("ATOMIC_CANCEL event={} reason={} committedTerrainContinues=true",state.event,reason);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        for(State state:new ArrayList<>(ACTIVE.values())){
            ServerPlayer caster=state.level.getServer().getPlayerList().getPlayer(state.caster);
            if(caster==null){ACTIVE.remove(state.caster);AtomicServer.cancelPending(state.caster);for(ServerPlayer viewer:state.level.getServer().getPlayerList().getPlayers())if(state.seen.contains(viewer.getUUID()))AtomicNetwork.end(viewer,state.event);continue;}
            if(caster.serverLevel()!=state.level||caster.isDeadOrDying()){cancel(caster,"invalid_state");continue;}
            long elapsed=state.releasing?AtomicClock.now()-state.releasedTime:0;
            center(state);
            if(state.releasing&&!state.impactLocked&&elapsed>=RELEASE_NANOS)lockImpact(caster,state.event,state.origin);
            else if(!state.impactLocked&&state.level.getGameTime()%2==0&&(state.lastSent==null||state.origin.distanceToSqr(state.lastSent)>.0001))broadcast(state);
            if(state.releasing&&elapsed>13_000_000_000L&&(AtomicTerrainSync.mayEnd(state.event)||elapsed>35_000_000_000L)){
                ACTIVE.remove(state.caster);for(ServerPlayer viewer:state.level.getServer().getPlayerList().getPlayers())if(state.seen.contains(viewer.getUUID()))AtomicNetwork.end(viewer,state.event);continue;
            }
            if(state.level.getGameTime()%40==0)for(ServerPlayer viewer:state.level.getServer().getPlayerList().getPlayers()){
                boolean visible=!viewer.isDeadOrDying()&&viewer.serverLevel()==state.level&&viewer.position().distanceToSqr(state.origin)<900*900;
                if(visible&&state.seen.add(viewer.getUUID()))AtomicNetwork.send(viewer,state.packet());
                else if(!visible&&state.seen.remove(viewer.getUUID()))AtomicNetwork.end(viewer,state.event);
            }
        }
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        DomainStasis.refresh();
        if(server!=null)for(ServerPlayer player:server.getPlayerList().getPlayers()){
            long now=server.getTickCount();
            // Snapshots, not a client real-time countdown: low TPS must not advertise early readiness.
            if(now%10==0||COOLDOWN.getOrDefault(player.getUUID(),-1L)==now)syncStatus(player);
        }
    }
    private static void forgetViewer(UUID uuid){for(State state:ACTIVE.values())state.seen.remove(uuid);}
    @SubscribeEvent public static void death(LivingDeathEvent event){if(event.getEntity() instanceof ServerPlayer player){cancel(player,"death");forgetViewer(player.getUUID());}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player){cancel(player,"logout");forgetViewer(player.getUUID());INPUT_AFTER.remove(player.getUUID());COOLDOWN.remove(player.getUUID());}}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer player){cancel(player,"dimension");forgetViewer(player.getUUID());}}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){ACTIVE.clear();COOLDOWN.clear();INPUT_AFTER.clear();}
}
