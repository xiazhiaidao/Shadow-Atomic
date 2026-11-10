package local.shadowatomic;

import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.SectionPos;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Cosmetic completion barrier. Never grants authority to apply blocks or damage. */
public final class AtomicTerrainSync {
    private static final Map<UUID,Work> WORK=new HashMap<>();
    private static final class Work {
        final UUID caster;final ServerLevel level;final List<CompletableFuture<Void>> geometry=new ArrayList<>();
        final Set<Long> chunks=new LinkedHashSet<>(),sections=new LinkedHashSet<>();boolean applied,reported,failed;long reportedAt,ownerReadyAt;
        Work(ServerPlayer player){caster=player.getUUID();level=player.serverLevel();}
    }
    public static void begin(ServerPlayer player,UUID id){WORK.put(id,new Work(player));}
    public static void chunk(UUID id,ChunkPos chunk,Collection<Long> sections,CompletableFuture<Void> sent){var w=WORK.get(id);if(w!=null){w.chunks.add(chunk.toLong());w.sections.addAll(sections);w.geometry.add(sent);}}
    public static void applied(UUID id){var w=WORK.get(id);if(w!=null)w.applied=true;}
    public static void fail(UUID id){var w=WORK.get(id);if(w!=null){w.failed=true;w.applied=true;}}
    public static boolean pending(UUID id){var w=WORK.get(id);return w!=null&&!w.reported;}
    public static boolean pendingIn(ServerLevel level){return WORK.values().stream().anyMatch(w->w.level==level&&!w.reported);}
    public static boolean mayEnd(UUID id){var w=WORK.get(id);return w==null||w.reported&&(w.failed||w.ownerReadyAt>0&&AtomicClock.now()-w.ownerReadyAt>6_000_000_000L);}
    public static void ownerReady(ServerPlayer player,UUID id){var w=WORK.get(id);if(w!=null&&w.reported&&player.getUUID().equals(w.caster)&&player.serverLevel()==w.level&&w.ownerReadyAt==0){w.ownerReadyAt=AtomicClock.now();AtomicMod.LOG.info("ATOMIC_TERRAIN_OWNER_READY event={} failed={}",id,w.failed);}}
    public static void send(ServerPlayer player,UUID id){
        var w=WORK.get(id);if(w==null||!w.reported||player.serverLevel()!=w.level)return;
        int view=player.server.getPlayerList().getViewDistance()+1;
        long[] relevant=w.sections.stream().filter(key->Math.abs(SectionPos.x(key)-player.chunkPosition().x)<=view&&Math.abs(SectionPos.z(key)-player.chunkPosition().z)<=view).mapToLong(Long::longValue).toArray();
        AtomicNetwork.terrain(player,new AtomicNetwork.Terrain(id,!w.failed,relevant));
    }
    public static void tick(){
        long now=AtomicClock.now();
        for(var e:WORK.entrySet()){
            Work w=e.getValue();if(w.reported||!w.applied)continue;
            if(!w.failed&&w.geometry.stream().anyMatch(f->!f.isDone()))continue;
            if(w.geometry.stream().anyMatch(CompletableFuture::isCompletedExceptionally))w.failed=true;
            w.reported=true;w.reportedAt=now;
            for(var p:w.level.players())send(p,e.getKey());
            AtomicMod.LOG.info("ATOMIC_TERRAIN_SENT event={} chunks={} sections={} success={}",e.getKey(),w.chunks.size(),w.sections.size(),!w.failed);
        }
        WORK.entrySet().removeIf(e->e.getValue().reported&&now-e.getValue().reportedAt>60_000_000_000L);
    }
    public static void clear(){WORK.clear();}
}
