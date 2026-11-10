package local.shadowatomic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Main-thread world work with durable, one-use reservations before every mutation.
 * An interrupted reservation is conservative: never manufacture a second copy.
 * Unloaded positions and blocked entity spawn positions remain eligible for retry. */
final class AtomicRecoveryEngine {
    final ServerLevel level;final UUID id;final Path folder;final CompoundTag meta;
    final List<Path> files;final Consumer<AtomicServer.RecoveryProgress> listener;
    final ShadowRanges.Scope scope;
    int fileIndex,index,phase,entityIndex;CompoundTag current,entityFile;
    long[] positions;int[] states;BlockState[] palette;BitSet changed,consumed,claims,candidates,restoredMask;
    CompletableFuture<CompoundTag> reading;CompletableFuture<Long> writing;
    long restored,revived,already,conflicts,unloaded,unsupported,uncertain,processed,total;
    final List<Vec3> samples=new ArrayList<>();
    AtomicRecoveryEngine(ServerLevel level,UUID id,Path folder,CompoundTag meta,ShadowRanges.Scope scope,Consumer<AtomicServer.RecoveryProgress> listener)throws Exception{
        this.level=level;this.id=id;this.folder=folder;this.meta=meta;this.scope=scope;this.listener=listener;
        Vec3 origin=new Vec3(meta.getDouble("x"),meta.getDouble("y"),meta.getDouble("z"));
        try(var list=Files.list(folder)){files=list.filter(p->p.getFileName().toString().matches("chunk_-?\\d+_-?\\d+\\.nbt")).sorted(Comparator.comparingDouble(p->{String[] s=p.getFileName().toString().replace(".nbt","").split("_");return origin.distanceToSqr(Integer.parseInt(s[1])*16+8,origin.y,Integer.parseInt(s[2])*16+8);})).toList();}
        total=Math.max(0,meta.getLong("changed"))+Math.max(0,meta.getInt("killed"));
    }
    void emit(boolean done,boolean success){
        var p=new AtomicServer.RecoveryProgress(id,total,processed,restored,revived,already,conflicts,unloaded,unsupported,uncertain,done,success,List.copyOf(samples));
        AtomicServer.lastRecovery=p;if(listener!=null)listener.accept(p);samples.clear();
    }
    private void sample(Vec3 p){if(samples.size()<24)samples.add(p);}
    boolean work(int budget,long end)throws Exception{
        int used=0;
        while(used++<budget&&System.nanoTime()<end){
            if(fileIndex<files.size()){
                Path path=files.get(fileIndex);
                if(current==null){
                    if(reading==null)reading=AtomicJournal.readAsync(path);
                    if(!reading.isDone())return false;
                    current=reading.join();reading=null;phase=0;
                    positions=current.getLongArray("positions");states=current.getIntArray("states");changed=BitSet.valueOf(current.getLongArray("changedIndices"));
                    consumed=BitSet.valueOf(current.getLongArray("consumedIndices"));restoredMask=BitSet.valueOf(current.getLongArray("restoredIndices"));consumed.or(restoredMask);
                    claims=BitSet.valueOf(current.getLongArray("recoveryClaims"));candidates=new BitSet();
                    if(states.length!=positions.length||changed.length()>positions.length)throw new IllegalArgumentException("Invalid block journal");
                    var tags=current.getList("palette",Tag.TAG_COMPOUND);palette=new BlockState[tags.size()];for(int i=0;i<palette.length;i++)palette[i]=NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),tags.getCompound(i));
                    index=positions.length-1;
                    if(!level.getChunkSource().hasChunk(current.getInt("chunkX"),current.getInt("chunkZ"))){unloaded+=changed.cardinality()-intersection(changed,consumed).cardinality();processed+=changed.cardinality();nextChunk();continue;}
                }
                if(phase==0){
                    if(index>=0){
                        int i=index--;if(!changed.get(i))continue;
                        if(scope!=null&&!scope.contains(Vec3.atCenterOf(BlockPos.of(positions[i])))){processed++;continue;}
                        if(consumed.get(i)){already++;processed++;continue;}
                        if(claims.get(i)){uncertain++;processed++;continue;}
                        if(states[i]<0||states[i]>=palette.length)throw new IllegalArgumentException("Invalid palette index");
                        BlockPos pos=BlockPos.of(positions[i]);BlockState before=palette[states[i]],now=level.getBlockState(pos);
                        if(now.equals(before)){consumed.set(i);restoredMask.set(i);already++;processed++;continue;}
                        // Never recreate containers or unbreakable states from a hand-edited journal.
                        if(before.hasBlockEntity()||before.getDestroySpeed(level,pos)<0){consumed.set(i);unsupported++;processed++;continue;}
                        if(!now.isAir()){consumed.set(i);conflicts++;processed++;continue;}
                        candidates.set(i);continue;
                    }
                    claims.or(candidates);persistMasks();writing=AtomicJournal.writeAsync(path,current.copy());phase=1;continue;
                }
                if(phase==1){if(!writing.isDone())return false;writing.join();writing=null;index=positions.length-1;phase=2;}
                if(phase==2){
                    if(index>=0){
                        int i=index--;if(!candidates.get(i))continue;
                        BlockPos pos=BlockPos.of(positions[i]);
                        if(!level.hasChunkAt(pos)){unloaded++;uncertain++;processed++;continue;}
                        BlockState before=palette[states[i]],now=level.getBlockState(pos);
                        if(now.equals(before)){already++;restoredMask.set(i);}
                        else if(!now.isAir()){conflicts++;}
                        else if(restoreBlock(pos,before)){restored++;restoredMask.set(i);sample(Vec3.atCenterOf(pos));}
                        else {conflicts++;}
                        consumed.set(i);claims.clear(i);processed++;continue;
                    }
                    persistMasks();writing=AtomicJournal.writeAsync(path,current.copy());phase=3;continue;
                }
                if(!writing.isDone())return false;writing.join();writing=null;
                AtomicServer.syncRecovery(level,new ChunkPos(current.getInt("chunkX"),current.getInt("chunkZ")));nextChunk();continue;
            }
            if(entityFile==null){
                if(reading==null)reading=AtomicJournal.readAsync(folder.resolve("entities.nbt"));if(!reading.isDone())return false;entityFile=reading.join();reading=null;phase=0;
            }
            ListTag entities=entityFile.getList("entities",Tag.TAG_COMPOUND);
            if(entityIndex<entities.size()){
                CompoundTag entry=entities.getCompound(entityIndex);
                if(phase==0){
                    if(!entry.getBoolean("killed")){entityIndex++;continue;}
                    if(scope!=null){var pos=entry.getCompound("before").getList("Pos",Tag.TAG_DOUBLE);if(pos.size()==3&&!scope.contains(new Vec3(pos.getDouble(0),pos.getDouble(1),pos.getDouble(2)))){processed++;entityIndex++;continue;}}
                    if(entry.getBoolean("restored")||entry.getBoolean("consumed")){already++;processed++;entityIndex++;continue;}
                    if(entry.getBoolean("recoveryClaim")){uncertain++;processed++;entityIndex++;continue;}
                    UUID uuid=entry.getUUID("uuid");boolean exists=false;for(var other:level.getServer().getAllLevels())if(other.getEntity(uuid)!=null)exists=true;
                    if(exists){conflicts++;processed++;entityIndex++;continue;}
                    CompoundTag before=entry.getCompound("before");String type=before.getString("id");
                    if(!type.startsWith("minecraft:")||type.equals("minecraft:wither")||type.equals("minecraft:ender_dragon")||before.contains("Passengers")||before.contains("RootVehicle")||before.contains("Leash")){
                        unsupported++;processed++;entityIndex++;continue;
                    }
                    ListTag pos=before.getList("Pos",Tag.TAG_DOUBLE);if(pos.size()!=3){unsupported++;processed++;entityIndex++;continue;}
                    BlockPos at=BlockPos.containing(pos.getDouble(0),pos.getDouble(1),pos.getDouble(2));
                    if(!level.hasChunkAt(at)){unloaded++;processed++;entityIndex++;continue;}
                    Entity entity=EntityType.loadEntityRecursive(before.copy(),level,e->e);
                    if(!(entity instanceof Mob)||entity.isMultipartEntity()||!entity.getUUID().equals(uuid)){unsupported++;processed++;entityIndex++;continue;}
                    if(!level.noCollision(entity,entity.getBoundingBox())||!level.getEntities(entity,entity.getBoundingBox()).isEmpty()){conflicts++;processed++;entityIndex++;continue;}
                    entry.putBoolean("recoveryClaim",true);writing=AtomicJournal.writeAsync(folder.resolve("entities.nbt"),entityFile.copy());phase=1;continue;
                }
                if(phase==1){
                    if(!writing.isDone())return false;writing.join();writing=null;
                    Entity entity=EntityType.loadEntityRecursive(entry.getCompound("before").copy(),level,e->e);
                    boolean exists=false;for(var other:level.getServer().getAllLevels())if(other.getEntity(entry.getUUID("uuid"))!=null)exists=true;
                    if(!exists&&entity instanceof Mob living&&level.hasChunkAt(living.blockPosition())&&level.noCollision(living,living.getBoundingBox())&&level.getEntities(living,living.getBoundingBox()).isEmpty()){
                        living.setHealth(living.getMaxHealth());living.deathTime=0;living.hurtTime=0;living.setDeltaMovement(Vec3.ZERO);
                        if(level.addFreshEntity(living)){entry.putBoolean("restored",true);revived++;sample(living.position());}else conflicts++;
                    }else conflicts++;
                    // A failed committed spawn is consumed too: a mod hook may have spawned it.
                    entry.putBoolean("consumed",true);entry.remove("recoveryClaim");processed++;
                    writing=AtomicJournal.writeAsync(folder.resolve("entities.nbt"),entityFile.copy());phase=2;continue;
                }
                if(!writing.isDone())return false;writing.join();writing=null;phase=0;entityIndex++;continue;
            }
            if(phase!=4){
                meta.putInt("recoveryFormat",2);meta.putLong("lastRecoveryTime",System.currentTimeMillis());meta.putLong("recoveredBlocks",meta.getLong("recoveredBlocks")+restored);meta.putLong("revivedEntities",meta.getLong("revivedEntities")+revived);
                writing=AtomicJournal.writeAsync(folder.resolve("event.nbt"),meta.copy());phase=4;continue;
            }
            if(!writing.isDone())return false;writing.join();writing=null;AtomicServer.lastRestored=restored;emit(true,true);
            AtomicMod.LOG.info("ATOMIC_RECOVERY_COMPLETE event={} restored={} revived={} conflicts={} unloaded={} unsupported={} uncertain={}",id,restored,revived,conflicts,unloaded,unsupported,uncertain);return true;
        }
        return false;
    }
    private static BitSet intersection(BitSet a,BitSet b){BitSet c=(BitSet)a.clone();c.and(b);return c;}
    private boolean restoreBlock(BlockPos pos,BlockState before){
        var owner=meta.hasUUID("caster")?level.getServer().getPlayerList().getPlayer(meta.getUUID("caster")):null;
        if(owner!=null&&!level.mayInteract(owner,pos))return false;
        var snapshot=net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(),level,pos);
        if(level.getChunkAt(pos).setBlockState(pos,before,false)==null)return false;
        boolean denied=net.minecraftforge.event.ForgeEventFactory.onBlockPlace(owner,snapshot,net.minecraft.core.Direction.UP);
        if(denied&&level.getBlockState(pos).equals(before))level.getChunkAt(pos).setBlockState(pos,snapshot.getReplacedBlock(),false);
        return !denied&&level.getBlockState(pos).equals(before);
    }
    private void persistMasks(){current.putInt("recoveryFormat",2);current.putLongArray("consumedIndices",consumed.toLongArray());current.putLongArray("restoredIndices",restoredMask.toLongArray());current.putLongArray("recoveryClaims",claims.toLongArray());}
    private void nextChunk(){fileIndex++;current=null;phase=0;}
}
