package local.shadowatomic;

/** Monotonic performance clock. Integrated single-player pause freezes both sides in this JVM.
 * Dedicated servers have no client pause publisher and always advance normally. */
public final class AtomicClock {
    private static long pausedAt,excluded;
    private static boolean paused;
    private AtomicClock(){}
    public static synchronized long now(){return (paused?pausedAt:System.nanoTime())-excluded;}
    public static synchronized void paused(boolean value){
        if(value==paused)return;long raw=System.nanoTime();
        if(value)pausedAt=raw;else excluded+=raw-pausedAt;
        paused=value;
    }
}
