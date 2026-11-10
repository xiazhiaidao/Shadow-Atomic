package local.shadowatomic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;

/** Sword blocking remains available. The removed dash request is deliberately inert. */
public final class ShadowCombat {
    // Retained for source compatibility with historical QA; no dash can start or move a player.
    public static final double DASH_DISTANCE=10;
    public static double lastDashDistance;public static long dashes,blocks;public static UUID lastDashPlayer,lastDashHit;
    public static boolean blocking(LivingEntity p){return !p.isDeadOrDying()&&p.isUsingItem()&&p.getUseItem().is(AtomicMod.SWORD.get());}
    public static boolean active(UUID player){return false;}
    public static void dash(ServerPlayer p){}
    public static void cancel(ServerPlayer p){}
}
