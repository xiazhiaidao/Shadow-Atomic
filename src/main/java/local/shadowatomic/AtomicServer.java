package local.shadowatomic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid=AtomicMod.ID)
public final class AtomicServer {
    private static final List<Cast> CASTS=new ArrayList<>();
    private static final List<Excavation> JOBS=new ArrayList<>();
    private static final List<AtomicRecoveryEngine> RECOVERIES=new ArrayList<>();
    private static final Map<UUID,Long> NO_LOOT=new HashMap<>();
    public static UUID lastEvent;
    public static long lastChanged,lastRestored;
    public record RecoveryProgress(UUID event,long total,long processed,long restored,long revived,long already,long conflicts,long unloaded,long unsupported,long uncertain,boolean done,boolean success,List<Vec3> samples){}
    public static RecoveryProgress lastRecovery;
    private static long tickStarted;
    public static boolean busy(ServerLevel level){return CASTS.stream().anyMatch(c->c.level==level)||JOBS.stream().anyMatch(j->j.level==level)||RECOVERIES.stream().anyMatch(j->j.level==level)||AtomicTerrainSync.pendingIn(level)||PlanetaryAtomic.activeIn(level)||RecoveryAtomic.activeIn(level);}
    public static void schedule(ServerPlayer player,UUID id,Vec3 origin,int radius,AtomicMode mode){
        if(!mode.damagesEntities())return;
        CASTS.add(new Cast(player.serverLevel(),player.getUUID(),id,origin,AtomicClock.now()+AtomicDomains.RELEASE_NANOS,radius,mode));lastEvent=id;
    }
    public static void cancelPending(UUID caster){CASTS.removeIf(c->c.caster.equals(caster));}
    private record Cast(ServerLevel level,UUID caster,UUID id,Vec3 origin,long fire,int radius,AtomicMode mode){}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){CASTS.clear();JOBS.clear();RECOVERIES.clear();NO_LOOT.clear();lastEvent=null;AtomicJournal.stopIO();AtomicTerrainSync.clear();}
    @SubscribeEvent public static void drops(LivingDropsEvent e){if(NO_LOOT.containsKey(e.getEntity().getUUID()))e.setCanceled(true);}
    @SubscribeEvent public static void experience(LivingExperienceDropEvent e){if(NO_LOOT.containsKey(e.getEntity().getUUID()))e.setCanceled(true);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase==TickEvent.Phase.START){tickStarted=System.nanoTime();return;}
        var iterator=CASTS.iterator();
        while(iterator.hasNext()){
            Cast c=iterator.next();if(AtomicClock.now()<c.fire)continue;iterator.remove();
            ServerPlayer player=c.level.getServer().getPlayerList().getPlayer(c.caster);
            if(player==null||player.serverLevel()!=c.level||player.isDeadOrDying())continue;
            Vec3 impact=AtomicDomains.lockImpact(player,c.id,c.origin);
            c=new Cast(c.level,c.caster,c.id,impact,c.fire,c.radius,c.mode);
            try{fire(c,player);}catch(Exception ex){AtomicMod.LOG.error("ATOMIC_ABORT journal could not be saved; no unrecorded destruction",ex);AtomicTerrainSync.fail(c.id);}
        }
        // Shared budget across jobs avoids multiplying work when several players cast.
        long now=System.nanoTime(),slack=Math.max(2_000_000L,45_000_000L-(now-tickStarted));
        long end=now+Math.min((long)(AtomicConfig.TERRAIN_BUDGET.get()*1_000_000),slack);
        int work=AtomicConfig.WORK.get();
        if(!JOBS.isEmpty()){
            Excavation j=JOBS.get(0);
            try{if(j.work(work,end)){JOBS.remove(0);}}catch(Exception ex){AtomicMod.LOG.error("ATOMIC_EXCAVATION_STOP journal retained for recovery",ex);AtomicTerrainSync.fail(j.cast.id);JOBS.remove(0);}
        }else if(!RECOVERIES.isEmpty()){
            AtomicRecoveryEngine r=RECOVERIES.get(0);
            try{if(r.work(Math.min(work,20000),System.nanoTime()+8_000_000L))RECOVERIES.remove(0);else if(r.level.getServer().getTickCount()%5==0)r.emit(false,true);}catch(Exception ex){AtomicMod.LOG.error("ATOMIC_RECOVERY_STOP",ex);r.emit(true,false);RECOVERIES.remove(0);}
        }
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server!=null)NO_LOOT.entrySet().removeIf(x->x.getValue()<server.getTickCount());
        AtomicTerrainSync.tick();
    }
    public static void targetStrike(ServerPlayer player,UUID event,Vec3 origin,LivingEntity target)throws Exception{
        if(!PlanetaryAtomic.validTarget(player,target))throw new IllegalArgumentException("Invalid planetary target");
        Path folder=AtomicJournal.folder(player.serverLevel(),event);CompoundTag meta=new CompoundTag();
        meta.putInt("format",1);meta.putUUID("event",event);meta.putUUID("caster",player.getUUID());meta.putString("dimension",player.level().dimension().location().toString());meta.putString("skill","planetary_atomic");meta.putString("mode","COMBAT");meta.putString("status","preparing");meta.putDouble("x",origin.x);meta.putDouble("y",origin.y);meta.putDouble("z",origin.z);meta.putInt("radius",0);meta.putUUID("target",target.getUUID());
        meta.putDouble("recoveryX",target.getX());meta.putDouble("recoveryY",target.getY());meta.putDouble("recoveryZ",target.getZ());
        AtomicJournal.write(folder.resolve("event.nbt"),meta);
        CompoundTag before=new CompoundTag();if(!target.save(before))throw new IllegalStateException("Target snapshot unavailable");
        CompoundTag entry=new CompoundTag();entry.putUUID("uuid",target.getUUID());entry.put("before",before);entry.putBoolean("killed",false);
        ListTag list=new ListTag();list.add(entry);CompoundTag entities=new CompoundTag();entities.put("entities",list);AtomicJournal.write(folder.resolve("entities.nbt"),entities);
        // Selection and recovery before-image both precede the sole normal damage call.
        NO_LOOT.put(target.getUUID(),(long)player.server.getTickCount()+240);
        skillHurt(target,player.level().damageSources().indirectMagic(player,player),AtomicConfig.DAMAGE.get().floatValue());
        entry.putBoolean("killed",skillDefeated(target));if(!skillDefeated(target))NO_LOOT.remove(target.getUUID());
        AtomicJournal.write(folder.resolve("entities.nbt"),entities);meta.putInt("killed",skillDefeated(target)?1:0);meta.putString("status","complete");AtomicJournal.write(folder.resolve("event.nbt"),meta);lastEvent=event;
    }
    /** Short realtime strike, using the same durable Recovery format as Atomic skills. */
    public static boolean shadowStrike(ServerPlayer player,UUID event,LivingEntity target,float damage)throws Exception{
        if(!ShadowPowers.eligible(player)||!PlanetaryAtomic.validTarget(player,target))return false;
        Path folder=AtomicJournal.folder(player.serverLevel(),event);CompoundTag meta=new CompoundTag();
        meta.putInt("format",1);meta.putUUID("event",event);meta.putUUID("caster",player.getUUID());
        meta.putString("dimension",player.level().dimension().location().toString());meta.putString("skill","shadow_melee");
        meta.putString("mode","COMBAT");meta.putString("status","preparing");
        meta.putDouble("x",target.getX());meta.putDouble("y",target.getY());meta.putDouble("z",target.getZ());meta.putInt("radius",0);
        meta.putDouble("recoveryX",target.getX());meta.putDouble("recoveryY",target.getY());meta.putDouble("recoveryZ",target.getZ());
        CompoundTag before=new CompoundTag();if(!target.save(before))throw new IllegalStateException("Target snapshot unavailable");
        CompoundTag entry=new CompoundTag();entry.putUUID("uuid",target.getUUID());entry.put("before",before);entry.putBoolean("killed",false);
        ListTag list=new ListTag();list.add(entry);CompoundTag entities=new CompoundTag();entities.put("entities",list);
        AtomicJournal.write(folder.resolve("event.nbt"),meta);AtomicJournal.write(folder.resolve("entities.nbt"),entities);
        boolean hit;
        NO_LOOT.put(target.getUUID(),(long)player.server.getTickCount()+240);
        try{hit=target.hurt(player.level().damageSources().playerAttack(player),damage);}
        finally{if(target.isAlive())NO_LOOT.remove(target.getUUID());}
        if(hit&&target.isAlive()){
            Vec3 away=target.position().subtract(player.position());
            target.knockback(1.5,-away.x,-away.z);target.hurtMarked=true;
        }
        entry.putBoolean("killed",!target.isAlive());meta.putInt("killed",target.isAlive()?0:1);meta.putString("status","complete");
        AtomicJournal.write(folder.resolve("entities.nbt"),entities);AtomicJournal.write(folder.resolve("event.nbt"),meta);lastEvent=event;
        if(hit)player.serverLevel().playSound(null,target.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,net.minecraft.sounds.SoundSource.PLAYERS,1,.7f);
        AtomicMod.LOG.info("SHADOW_STRIKE event={} target={} damage={} hit={} killed={} journal={}",event,target.getUUID(),damage,hit,!target.isAlive(),folder);
        return hit;
    }
    private static void fire(Cast c,ServerPlayer player)throws Exception{
        if(c.mode.destroysTerrain())AtomicTerrainSync.begin(player,c.id);
        Path folder=AtomicJournal.folder(c.level,c.id);
        CompoundTag meta=new CompoundTag();meta.putInt("format",1);meta.putUUID("event",c.id);meta.putString("dimension",c.level.dimension().location().toString());meta.putUUID("caster",c.caster);meta.putDouble("x",c.origin.x);meta.putDouble("y",c.origin.y);meta.putDouble("z",c.origin.z);meta.putInt("radius",c.radius);meta.putString("status","preparing");
        meta.putString("mode",c.mode.name());
        AtomicJournal.write(folder.resolve("event.nbt"),meta);
        ListTag victims=new ListTag();List<LivingEntity> targets=new ArrayList<>();
        AABB box=new AABB(c.origin,c.origin).inflate(c.radius);
        for(LivingEntity entity:c.level.getEntitiesOfClass(LivingEntity.class,box)){
            if(entity instanceof Player||entity instanceof ArmorStand||!entity.isAlive()||entity.isAlliedTo(player)||entity.distanceToSqr(c.origin)>c.radius*c.radius)continue;
            if(entity instanceof TamableAnimal pet&&player.getUUID().equals(pet.getOwnerUUID()))continue;
            CompoundTag saved=new CompoundTag();if(!entity.save(saved))continue;
            CompoundTag entry=new CompoundTag();entry.putUUID("uuid",entity.getUUID());entry.put("before",saved);entry.putBoolean("killed",false);victims.add(entry);targets.add(entity);
        }
        CompoundTag entityFile=new CompoundTag();entityFile.put("entities",victims);
        // Before-image is durable before hurt() can trigger death or drops.
        AtomicJournal.write(folder.resolve("entities.nbt"),entityFile);
        int killed=0;
        for(int i=0;i<targets.size();i++){
            LivingEntity entity=targets.get(i);NO_LOOT.put(entity.getUUID(),(long)player.server.getTickCount()+240);
            double distance=Math.sqrt(entity.distanceToSqr(c.origin))/c.radius;
            skillHurt(entity,c.level.damageSources().indirectMagic(player,player),(float)(AtomicConfig.DAMAGE.get()*(1-.8*distance)));
            if(skillDefeated(entity)){victims.getCompound(i).putBoolean("killed",true);killed++;}
            else{NO_LOOT.remove(entity.getUUID());Vec3 delta=entity.position().subtract(c.origin).normalize();entity.setDeltaMovement(delta.scale(2).add(0,.65,0));entity.hurtMarked=true;}
        }
        AtomicJournal.write(folder.resolve("entities.nbt"),entityFile);
        meta.putInt("killed",killed);meta.putString("status",c.mode.destroysTerrain()?"excavating":"complete");AtomicJournal.write(folder.resolve("event.nbt"),meta);
        if(c.mode.destroysTerrain())JOBS.add(new Excavation(c,player,folder,meta));
        AtomicMod.LOG.info("ATOMIC_GAMEPLAY_BLAST event={} radius={} killed={} mode={} journal={}",c.id,c.radius,killed,c.mode,folder);
    }
    /** Use the dragon's head to avoid parent/body reduction, after saving the before-image. */
    private static boolean skillHurt(LivingEntity target,DamageSource source,float amount){
        if(amount<=0)return false;
        return target instanceof EnderDragon dragon?dragon.hurt(dragon.head,source,amount):target.hurt(source,amount);
    }
    private static boolean skillDefeated(LivingEntity target){
        // Vanilla airborne dragons retain one HP while irreversibly entering their death flight.
        return !target.isAlive()||target instanceof EnderDragon dragon&&dragon.getPhaseManager().getCurrentPhase().getPhase()==EnderDragonPhase.DYING;
    }
    private static CompletableFuture<Void> sync(ServerLevel level,ChunkPos pos){
        LevelChunk chunk=level.getChunkSource().getChunkNow(pos.x,pos.z);if(chunk==null)return CompletableFuture.completedFuture(null);
        chunk.setUnsaved(true);chunk.setLightCorrect(false);
        // Final real geometry is independent of the lighting worker's backlog.
        // It is queued only after the applied recovery mask is saved. Later light
        // packets update illumination without resending/rebuilding every block.
        sendChunk(level,pos);
        level.getChunkSource().getLightEngine().lightChunk(chunk,false).whenComplete((result,error)->level.getServer().execute(()->{
            if(error!=null){AtomicMod.LOG.error("ATOMIC_RELIGHT_FAILED chunk={}",pos,error);return;}
            if(level.getChunkSource().getChunkNow(pos.x,pos.z)!=chunk)return;
            var packet=new ClientboundLightUpdatePacket(pos,level.getLightEngine(),null,null);
            int view=level.getServer().getPlayerList().getViewDistance()+1;
            for(ServerPlayer p:level.players())if(Math.abs(p.chunkPosition().x-pos.x)<=view&&Math.abs(p.chunkPosition().z-pos.z)<=view)p.connection.send(packet);
        }));return CompletableFuture.completedFuture(null);
    }
    private static void sendChunk(ServerLevel level,ChunkPos pos){
        LevelChunk chunk=level.getChunkSource().getChunkNow(pos.x,pos.z);if(chunk==null)return;
        var packet=new ClientboundLevelChunkWithLightPacket(chunk,level.getLightEngine(),null,null);
        int view=level.getServer().getPlayerList().getViewDistance()+1;
        for(ServerPlayer p:level.players())if(Math.abs(p.chunkPosition().x-pos.x)<=view&&Math.abs(p.chunkPosition().z-pos.z)<=view)p.connection.send(packet);
    }
    private static final class Excavation {
        final ServerLevel level;final ServerPlayer player;final Cast cast;final Path folder;final CompoundTag meta;
        final List<ChunkPos> chunks=new ArrayList<>();int chunkIndex,columnIndex,y=Integer.MIN_VALUE,lowest;
        final List<Long> positions=new ArrayList<>();final List<BlockState> states=new ArrayList<>();
        final ArrayDeque<Batch> prepared=new ArrayDeque<>(),persisting=new ArrayDeque<>();
        long changed,scanNanos,applyNanos,journalNanos,ioNanos,totalWorkNanos,scanned,applied;
        int workCalls,hardExits,timeExits,ioWaits;
        final class Batch {
            final ChunkPos chunk;final long[] positions;final BlockState[] before;final CompoundTag tag;final Path path;
            final BitSet changedIndices=new BitSet();final CompletableFuture<Long> durableBefore;
            CompletableFuture<Long> durableAfter;int index;boolean beforeAccounted;
            Batch(ChunkPos chunk){
                long at=System.nanoTime();this.chunk=chunk;positions=new long[Excavation.this.positions.size()];before=states.toArray(BlockState[]::new);
                tag=new CompoundTag();tag.putString("status","prepared");tag.putInt("chunkX",chunk.x);tag.putInt("chunkZ",chunk.z);
                int[] stateIds=new int[positions.length];ListTag palette=new ListTag();Map<BlockState,Integer> ids=new HashMap<>();
                for(int i=0;i<positions.length;i++){
                    positions[i]=Excavation.this.positions.get(i);BlockState state=before[i];Integer id=ids.get(state);
                    if(id==null){id=ids.size();ids.put(state,id);palette.add(NbtUtils.writeBlockState(state));}stateIds[i]=id;
                }
                tag.putLongArray("positions",positions);tag.putIntArray("states",stateIds);tag.put("palette",palette);tag.putLongArray("changedIndices",new long[0]);
                path=folder.resolve("chunk_"+chunk.x+"_"+chunk.z+".nbt");journalNanos+=System.nanoTime()-at;
                durableBefore=AtomicJournal.writeAsync(path,tag);
            }
        }
        final long started=System.nanoTime();
        Excavation(Cast cast,ServerPlayer player,Path folder,CompoundTag meta){
            this.level=cast.level;this.player=player;this.cast=cast;this.folder=folder;this.meta=meta;
            int r=cast.radius,cx=(int)Math.floor(cast.origin.x),cz=(int)Math.floor(cast.origin.z);
            for(int x=(cx-r)>>4;x<=(cx+r)>>4;x++)for(int z=(cz-r)>>4;z<=(cz+r)>>4;z++)if(level.getChunkSource().hasChunk(x,z))chunks.add(new ChunkPos(x,z));
            chunks.sort(Comparator.comparingDouble(p->Math.pow(p.getMiddleBlockX()-cx,2)+Math.pow(p.getMiddleBlockZ()-cz,2)));
        }
        boolean work(int budget,long end)throws Exception{
            long callAt=System.nanoTime();workCalls++;int used=0;
            while(used<budget&&System.nanoTime()<end){
                // Applied masks must be durable before synchronization. Worker threads
                // receive only owned NBT data; all world access remains on this thread.
                for(var it=persisting.iterator();it.hasNext();){
                    Batch batch=it.next();if(!batch.durableAfter.isDone())continue;
                    ioNanos+=batch.durableAfter.join();
                    Set<Long> sections=new HashSet<>();
                    for(int bit=batch.changedIndices.nextSetBit(0);bit>=0;bit=batch.changedIndices.nextSetBit(bit+1))sections.add(net.minecraft.core.SectionPos.of(BlockPos.of(batch.positions[bit])).asLong());
                    AtomicTerrainSync.chunk(cast.id,batch.chunk,sections,sync(level,batch.chunk));it.remove();
                }
                Batch batch=prepared.peekFirst();
                if(batch!=null&&batch.durableBefore.isDone()){
                    if(!batch.beforeAccounted){ioNanos+=batch.durableBefore.join();batch.beforeAccounted=true;}
                    if(!level.getChunkSource().hasChunk(batch.chunk.x,batch.chunk.z))batch.index=batch.positions.length;
                    long applyAt=System.nanoTime();
                    while(batch.index<batch.positions.length&&used++<budget&&System.nanoTime()<end){
                        applied++;
                        int index=batch.index++;BlockPos pos=BlockPos.of(batch.positions[index]);BlockState before=batch.before[index];
                        if(!level.getBlockState(pos).equals(before))continue;
                        var breakEvent=new net.minecraftforge.event.level.BlockEvent.BreakEvent(level,pos,before,player);
                        if(!level.mayInteract(player,pos)||net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(breakEvent))continue;
                        // Protection listeners may have changed this position while handling the event.
                        if(!level.getBlockState(pos).equals(before))continue;
                        if(level.getChunkAt(pos).setBlockState(pos,Blocks.AIR.defaultBlockState(),false)!=null){changed++;batch.changedIndices.set(index);}
                    }
                    applyNanos+=System.nanoTime()-applyAt;
                    if(batch.index==batch.positions.length){
                        // durableBefore has completed and will no longer read this tag.
                        batch.tag.putString("status","applied");batch.tag.putLongArray("changedIndices",batch.changedIndices.toLongArray());
                        batch.durableAfter=AtomicJournal.writeAsync(batch.path,batch.tag);prepared.removeFirst();persisting.addLast(batch);
                    }
                    continue;
                }
                if(chunkIndex>=chunks.size()||prepared.size()+persisting.size()>=8){ioWaits++;break;}
                ChunkPos chunk=chunks.get(chunkIndex);
                if(!level.getChunkSource().hasChunk(chunk.x,chunk.z)){next();continue;}
                long scanAt=System.nanoTime();
                while(columnIndex<256&&used<budget&&System.nanoTime()<end){
                    int x=chunk.getMinBlockX()+(columnIndex&15),z=chunk.getMinBlockZ()+(columnIndex>>4);
                    double dx=x+.5-cast.origin.x,dz=z+.5-cast.origin.z,rr=(dx*dx+dz*dz)/(cast.radius*cast.radius);
                    // A small caster foothold keeps survival users from falling into the crater.
                    if(rr>1||dx*dx+dz*dz<4){columnIndex++;y=Integer.MIN_VALUE;continue;}
                    if(y==Integer.MIN_VALUE){y=Math.min(level.getMaxBuildHeight()-1,level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)+1);lowest=Math.max(level.getMinBuildHeight(),(int)Math.floor(cast.origin.y-AtomicConfig.DEPTH.get()*Math.pow(1-rr,.65)));}
                    while(y>=lowest&&used++<budget&&System.nanoTime()<end){
                        scanned++;
                        BlockPos pos=new BlockPos(x,y--,z);BlockState state=level.getBlockState(pos);
                        if(state.isAir()||state.getDestroySpeed(level,pos)<0||state.hasBlockEntity())continue;
                        positions.add(pos.asLong());states.add(state);
                    }
                    if(y<lowest){columnIndex++;y=Integer.MIN_VALUE;}
                }
                scanNanos+=System.nanoTime()-scanAt;
                if(columnIndex>=256){if(!positions.isEmpty())prepared.addLast(new Batch(chunk));next();}
            }
            if(chunkIndex<chunks.size()||!prepared.isEmpty()||!persisting.isEmpty()){account(callAt,budget,used,end);return false;}
            lastChanged=changed;meta.putString("status","complete");meta.putLong("changed",changed);AtomicJournal.write(folder.resolve("event.nbt"),meta);
            AtomicTerrainSync.applied(cast.id);
            account(callAt,budget,used,end);
            AtomicMod.LOG.info("ATOMIC_TERRAIN_COST event={} calls={} scanned={} applied={} hardExits={} timeExits={} scanMs={} applyMs={} journalMs={} totalWorkMs={} wallMs={}",cast.id,workCalls,scanned,applied,hardExits,timeExits,scanNanos/1_000_000,applyNanos/1_000_000,journalNanos/1_000_000,totalWorkNanos/1_000_000,(System.nanoTime()-started)/1_000_000);
            AtomicMod.LOG.info("ATOMIC_TERRAIN_PIPELINE event={} ioWorkerMs={} ioWaitCalls={} capacity=8 workers=2",cast.id,ioNanos/1_000_000,ioWaits);
            AtomicMod.LOG.info("ATOMIC_TERRAIN_COMPLETE event={} changed={} chunks={} ms={}",cast.id,changed,chunks.size(),(System.nanoTime()-started)/1_000_000);return true;
        }
        void account(long callAt,int budget,int used,long end){totalWorkNanos+=System.nanoTime()-callAt;if(used>=budget)hardExits++;if(System.nanoTime()>=end)timeExits++;}
        void next(){chunkIndex++;columnIndex=0;y=Integer.MIN_VALUE;positions.clear();states.clear();}
    }
    public static void recover(ServerLevel level,UUID id)throws Exception{
        if(busy(level))throw new IllegalStateException("Wait for the current world operation to finish");
        queueRecovery(level,id,null);
    }
    static void recoverForSkill(ServerLevel level,UUID id,java.util.function.Consumer<RecoveryProgress> listener)throws Exception{
        recoverForSkill(level,id,null,listener);
    }
    static void recoverForSkill(ServerLevel level,UUID id,ShadowRanges.Scope scope,java.util.function.Consumer<RecoveryProgress> listener)throws Exception{
        if(!CASTS.isEmpty()||!JOBS.isEmpty()||!RECOVERIES.isEmpty()||AtomicTerrainSync.pendingIn(level)||PlanetaryAtomic.activeIn(level))throw new IllegalStateException("World operation busy");
        queueRecovery(level,id,scope,listener);
    }
    private static void queueRecovery(ServerLevel level,UUID id,java.util.function.Consumer<RecoveryProgress> listener)throws Exception{
        queueRecovery(level,id,null,listener);
    }
    private static void queueRecovery(ServerLevel level,UUID id,ShadowRanges.Scope scope,java.util.function.Consumer<RecoveryProgress> listener)throws Exception{
        Path folder=AtomicJournal.folder(level,id);CompoundTag meta=AtomicJournal.read(folder.resolve("event.nbt"));
        if(meta.getInt("format")!=1||!meta.hasUUID("event")||!meta.getUUID("event").equals(id)||!meta.getString("status").equals("complete"))throw new IllegalArgumentException("Incomplete or unsupported event");
        if(!meta.getString("dimension").equals(level.dimension().location().toString()))throw new IllegalArgumentException("Wrong dimension");
        RECOVERIES.add(new AtomicRecoveryEngine(level,id,folder,meta,scope,listener));
    }
    static void syncRecovery(ServerLevel level,ChunkPos pos){sync(level,pos);}
}
