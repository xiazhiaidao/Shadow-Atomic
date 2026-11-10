package local.shadowatomic;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Deflater;
import java.util.UUID;
import java.util.concurrent.*;

/** Durable before-images, written before any destructive operation. */
public final class AtomicJournal {
    private static ExecutorService io;
    private static synchronized ExecutorService executor(){
        if(io==null||io.isShutdown())io=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"AtomicJournalIO");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        return io;
    }
    /** Caller owns an immutable tag until this future completes. No world access off-thread. */
    public static CompletableFuture<Long> writeAsync(Path path,CompoundTag snapshot){
        return CompletableFuture.supplyAsync(()->{long at=System.nanoTime();try{write(path,snapshot);return System.nanoTime()-at;}catch(IOException error){throw new CompletionException(error);}},executor());
    }
    public static CompletableFuture<CompoundTag> readAsync(Path path){
        return CompletableFuture.supplyAsync(()->{try{return read(path);}catch(IOException error){throw new CompletionException(error);}},executor());
    }
    public static synchronized void stopIO(){
        if(io==null)return;io.shutdown();try{if(!io.awaitTermination(10,TimeUnit.SECONDS))io.shutdownNow();}catch(InterruptedException error){Thread.currentThread().interrupt();io.shutdownNow();}io=null;
    }
    public static Path folder(ServerLevel level,UUID id){return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/shadow_atomic").resolve(id.toString());}
    public static void write(Path path,CompoundTag data) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp=path.resolveSibling(path.getFileName()+".tmp");
        // Same gzip/NBT format as the old journal. Level 1 favors a short recovery
        // write over minimum disk size; owned snapshots never touch the world here.
        try(var out=new DataOutputStream(new BufferedOutputStream(new FastGzip(Files.newOutputStream(temp))))){NbtIo.write(data,out);}
        try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
    private static final class FastGzip extends GZIPOutputStream {
        FastGzip(java.io.OutputStream output)throws IOException{super(output,65536);def.setLevel(Deflater.BEST_SPEED);}
    }
    public static CompoundTag read(Path path)throws IOException{return NbtIo.readCompressed(path.toFile());}
}
