package local.shadowatomic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Owner-only, same-dimension restoration. No coordinates or NBT are trusted from C2S. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID)
public final class RecoveryAtomic {
    public static final double PRELUDE=2.4;public static final int RANGE=384;
    private static final Map<UUID,State> ACTIVE=new HashMap<>();
    private static final Map<UUID,Long> COOLDOWN=new HashMap<>(),CATALOG_AFTER=new HashMap<>();
    private static final class State {
        final ServerLevel level;final UUID owner,event;final int caster,radius;final Vec3 origin;final long start=AtomicClock.now();
        final Set<UUID> viewers=new HashSet<>();boolean committed,detached,done;long ended;
        AtomicServer.RecoveryProgress progress;
        State(ServerPlayer p,UUID id,CompoundTag meta){level=p.serverLevel();owner=p.getUUID();event=id;caster=p.getId();origin=p.position();radius=ShadowRanges.get(p).recovery();}
        double age(){return (AtomicClock.now()-start)/1e9;}
        AtomicNetwork.RecoveryState packet(){var q=progress;return new AtomicNetwork.RecoveryState(event,caster,origin,radius,level.dimension().location(),Math.max(0,(AtomicClock.now()-start)/1_000_000),committed,done,q==null||q.success(),q==null?0:q.total(),q==null?0:q.processed(),q==null?0:q.restored(),q==null?0:q.revived(),q==null?0:q.already(),q==null?0:q.conflicts(),q==null?0:q.unloaded(),q==null?0:q.unsupported(),q==null?0:q.uncertain(),q==null?List.of():q.samples());}
    }
    public static boolean active(UUID p){return ACTIVE.containsKey(p);}
    public static boolean activeIn(ServerLevel l){return ACTIVE.values().stream().anyMatch(s->s.level==l);}
    public static UUID event(UUID p){var s=ACTIVE.get(p);return s==null?null:s.event;}
    private static Vec3 origin(CompoundTag m){return m.contains("recoveryX")?new Vec3(m.getDouble("recoveryX"),m.getDouble("recoveryY"),m.getDouble("recoveryZ")):new Vec3(m.getDouble("x"),m.getDouble("y"),m.getDouble("z"));}
    private static boolean equipped(ServerPlayer p){return p.getMainHandItem().is(AtomicMod.SWORD.get())||p.getOffhandItem().is(AtomicMod.SWORD.get());}
    public static boolean eligible(ServerPlayer p,UUID id,CompoundTag m){return m.getInt("format")==1&&m.hasUUID("event")&&m.getUUID("event").equals(id)&&m.hasUUID("caster")&&m.getUUID("caster").equals(p.getUUID())&&m.getString("dimension").equals(p.level().dimension().location().toString())&&m.getString("status").equals("complete")&&origin(m).distanceToSqr(p.position())<=RANGE*RANGE&&(m.getLong("changed")>0||m.getInt("killed")>0);}
    public static void catalog(ServerPlayer p){
        long now=p.server.getTickCount();if(CATALOG_AFTER.getOrDefault(p.getUUID(),0L)>now)return;CATALOG_AFTER.put(p.getUUID(),now+20);
        if(!equipped(p)||p.isDeadOrDying()||p.isSpectator())return;
        List<AtomicNetwork.RecoveryEntry> result=new ArrayList<>();String message="";
        Path root=AtomicJournal.folder(p.serverLevel(),new UUID(0,0)).getParent();
        try{
            if(Files.exists(root))try(var dirs=Files.list(root)){
                // Bound per-request work; journal storage is intentionally never auto-deleted.
                var paths=dirs.filter(Files::isDirectory).sorted(Comparator.comparingLong(RecoveryAtomic::modified).reversed()).limit(512).toList();
                for(Path folder:paths){try{UUID id=UUID.fromString(folder.getFileName().toString());CompoundTag m=AtomicJournal.read(folder.resolve("event.nbt"));if(eligible(p,id,m)){result.add(new AtomicNetwork.RecoveryEntry(id,origin(m),m.getInt("radius"),m.getLong("changed"),m.getInt("killed"),m.getLong("recoveredBlocks"),m.getLong("revivedEntities")));if(result.size()==32)break;}}catch(Exception ignored){}}
            }
        }catch(Exception e){message="recovery.shadow_atomic.catalog_error";AtomicMod.LOG.warn("RECOVERY_CATALOG_FAIL",e);}
        AtomicNetwork.recoveryCatalog(p,new AtomicNetwork.RecoveryCatalog(p.level().dimension().location(),List.copyOf(result),message));
    }
    private static long modified(Path p){try{return Files.getLastModifiedTime(p.resolve("event.nbt")).toMillis();}catch(Exception e){return 0;}}
    public static void request(ServerPlayer p,UUID id){
        if(!equipped(p)||p.isDeadOrDying()||p.isSpectator())return;
        if(active(p.getUUID())||AtomicDomains.event(p.getUUID())!=null||AtomicServer.busy(p.serverLevel())||COOLDOWN.getOrDefault(p.getUUID(),0L)>p.server.getTickCount()){p.displayClientMessage(Component.translatable("message.shadow_atomic.busy"),true);return;}
        try{
            CompoundTag m=AtomicJournal.read(AtomicJournal.folder(p.serverLevel(),id).resolve("event.nbt"));
            if(!eligible(p,id,m))throw new IllegalArgumentException("Unavailable recovery event");
            State s=new State(p,id,m);ACTIVE.put(p.getUUID(),s);COOLDOWN.put(p.getUUID(),(long)p.server.getTickCount()+AtomicConfig.RECOVERY_COOLDOWN.get());broadcast(s);
            AtomicMod.LOG.info("RECOVERY_BEGIN event={} owner={} preludeSeconds={}",id,p.getUUID(),PRELUDE);
        }catch(Exception e){p.displayClientMessage(Component.translatable("recovery.shadow_atomic.unavailable"),true);}
    }
    private static void broadcast(State s){if(s.detached)return;for(var p:s.level.players())if(!p.isDeadOrDying()&&p.position().distanceToSqr(s.origin)<600*600){s.viewers.add(p.getUUID());AtomicNetwork.recoveryState(p,s.packet());}}
    private static void end(State s){for(var p:s.level.getServer().getPlayerList().getPlayers())if(s.viewers.contains(p.getUUID()))AtomicNetwork.recoveryEnd(p,s.event);s.viewers.clear();}
    public static void cancel(ServerPlayer p,String reason){State s=ACTIVE.get(p.getUUID());if(s==null)return;end(s);if(s.committed){s.detached=true;}else ACTIVE.remove(p.getUUID());AtomicMod.LOG.info("RECOVERY_CANCEL event={} reason={} committed={} committedWorkContinues={}",s.event,reason,s.committed,s.committed);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent t){
        if(t.phase!=TickEvent.Phase.END)return;
        for(State s:new ArrayList<>(ACTIVE.values())){
            var p=s.level.getServer().getPlayerList().getPlayer(s.owner);
            if(p==null||p.isDeadOrDying()||p.serverLevel()!=s.level||p.isSpectator()){
                if(!s.detached){end(s);s.detached=true;}if(!s.committed){ACTIVE.remove(s.owner);continue;}
            }
            if(!s.committed&&s.age()>=PRELUDE){
                try{var m=AtomicJournal.read(AtomicJournal.folder(s.level,s.event).resolve("event.nbt"));if(!eligible(p,s.event,m))throw new IllegalStateException("Event no longer available");
                    AtomicServer.recoverForSkill(s.level,s.event,new ShadowRanges.Scope(s.origin,s.radius),q->{s.progress=q;if(q.done()){s.done=true;s.ended=AtomicClock.now();if(q.success()&&p!=null&&p.serverLevel()==s.level&&!p.isDeadOrDying())p.sendSystemMessage(Component.translatable("recovery.shadow_atomic.result",q.restored(),q.revived(),q.conflicts(),q.unloaded(),q.unsupported(),q.uncertain()));}broadcast(s);});s.committed=true;broadcast(s);
                }catch(Exception e){AtomicMod.LOG.error("RECOVERY_COMMIT_ABORT",e);end(s);ACTIVE.remove(s.owner);continue;}
            }
            if(s.done&&(s.detached||AtomicClock.now()-s.ended>2_500_000_000L)){end(s);ACTIVE.remove(s.owner);continue;}
            if(!s.detached&&s.level.getServer().getTickCount()%20==0){broadcast(s);s.progress=s.progress==null?null:new AtomicServer.RecoveryProgress(s.progress.event(),s.progress.total(),s.progress.processed(),s.progress.restored(),s.progress.revived(),s.progress.already(),s.progress.conflicts(),s.progress.unloaded(),s.progress.unsupported(),s.progress.uncertain(),s.progress.done(),s.progress.success(),List.of());}
            // Visibility changes immediately release the old dimension's visuals.
            for(UUID viewer:new HashSet<>(s.viewers)){var v=s.level.getServer().getPlayerList().getPlayer(viewer);if(v==null||v.serverLevel()!=s.level||v.isDeadOrDying()||v.position().distanceToSqr(s.origin)>=600*600){if(v!=null)AtomicNetwork.recoveryEnd(v,s.event);s.viewers.remove(viewer);}}
        }
    }
    @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p,"death");}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){cancel(p,"logout");CATALOG_AFTER.remove(p.getUUID());COOLDOWN.remove(p.getUUID());}}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p,"dimension");}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){ACTIVE.clear();COOLDOWN.clear();CATALOG_AFTER.clear();}
}
