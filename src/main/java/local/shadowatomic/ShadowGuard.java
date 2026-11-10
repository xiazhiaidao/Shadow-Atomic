package local.shadowatomic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/**
 * Server-authoritative "the sword cannot be hurt" rule for the hooded shadow form.
 *
 * <p>Only ordinary damage is filtered. Anything that bypasses invulnerability (the void,
 * /kill, and any datapack damage type in that tag) still applies, so the player can always
 * be removed by a server operator or by falling out of the world. The filter runs before
 * any hurt/death handling, so it never reorders recovery journal writes: the journal is
 * written by the skill code that would have dealt the damage, and this class only refuses
 * the damage itself.
 */
@Mod.EventBusSubscriber(modid=AtomicMod.ID)
public final class ShadowGuard {
    /** QA-visible counters; they are plain fields so an in-engine probe can read them. */
    public static long blocked;
    /** Raw count of ordinary attack events seen for any entity, used by the QA probe only. */
    public static long seen;
    public static long lethalPassed;
    public static String lastSource="none";
    public static UUID lastDefender;
    private static long lastLogTick;

    /** Server-side truth: the robe form is drawn for anyone holding the sword in either hand. */
    public static boolean holding(LivingEntity entity){
        return entity.getMainHandItem().is(AtomicMod.SWORD.get())||entity.getOffhandItem().is(AtomicMod.SWORD.get());
    }

    public static boolean protectedPlayer(LivingEntity entity){
        if(!AtomicConfig.SHADOW_GUARD.get()&&!ShadowCombat.blocking(entity))return false;
        if(!(entity instanceof ServerPlayer player))return false;
        if(player.level().isClientSide||player.isSpectator()||player.isDeadOrDying())return false;
        if(player.isCreative())return false;
        return holding(player);
    }

    /** Void, /kill and anything else tagged as bypassing invulnerability must remain lethal. */
    public static boolean lethal(DamageSource source){
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
            ||source.is(DamageTypes.FELL_OUT_OF_WORLD)
            ||source.is(DamageTypes.GENERIC_KILL);
    }

    public static boolean guarded(LivingEntity entity,DamageSource source){
        if(!protectedPlayer(entity)||lethal(source))return false;
        if(AtomicConfig.SHADOW_GUARD.get())return true;
        Vec3 at=source.getSourcePosition();
        Vec3 direction=at==null?Vec3.ZERO:at.subtract(entity.position());
        if(source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile arrow&&arrow.getDeltaMovement().lengthSqr()>1e-6)direction=arrow.getDeltaMovement().reverse();
        return ShadowCombat.blocking(entity)&&direction.normalize().dot(entity.getLookAngle())>.15;
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent event){
        seen++;
        if(!guarded(event.getEntity(),event.getSource()))return;
        event.setCanceled(true);
        record(event.getEntity(),event.getSource(),event.getAmount());
    }

    /** Defense in depth: another mod may post hurt without an attack event reaching vanilla. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void hurt(LivingHurtEvent event){
        if(!guarded(event.getEntity(),event.getSource()))return;
        event.setCanceled(true);
        record(event.getEntity(),event.getSource(),event.getAmount());
    }

    private static void record(LivingEntity entity,DamageSource source,float amount){
        ServerPlayer player=(ServerPlayer)entity;
        if(ShadowCombat.blocking(player)){
            ShadowCombat.blocks++;
            if(source.getEntity() instanceof LivingEntity attacker&&!(attacker instanceof net.minecraft.world.entity.player.Player)&&!attacker.isAlliedTo(player)){
                Vec3 away=attacker.position().subtract(player.position());attacker.knockback(.7,-away.x,-away.z);attacker.hurtMarked=true;
            }
            if(player.server.getTickCount()%4==0)player.serverLevel().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,net.minecraft.sounds.SoundSource.PLAYERS,.6f,.8f);
        }
        blocked++;lastDefender=player.getUUID();lastSource=source.getMsgId();
        ServerLevel level=player.serverLevel();
        if(level.getServer().getTickCount()-lastLogTick>=20){
            lastLogTick=level.getServer().getTickCount();
            AtomicMod.LOG.info("ATOMIC_GUARD_BLOCK player={} source={} amount={} blocked={}",player.getGameProfile().getName(),lastSource,amount,blocked);
        }
        // Observers within chat range get the same spark and arc, so the effect is readable in multiplayer.
        Vec3 from=source.getSourcePosition();
        Vec3 direction=from==null?player.getLookAngle().reverse():from.subtract(player.position());
        if(direction.lengthSqr()<1.0E-6)direction=new Vec3(0,0,1);
        direction=direction.normalize();
        Vec3 point=player.position().add(0,player.getBbHeight()*.62,0).add(direction.scale(player.getBbWidth()*.55+.14));
        var packet=new AtomicNetwork.Guard(player.getId(),point,direction,Math.min(amount,999f));
        for(ServerPlayer viewer:level.players())if(viewer.distanceToSqr(player)<48*48)AtomicNetwork.guard(viewer,packet);
    }

    public static void reset(){
        blocked=0;lethalPassed=0;lastSource="none";lastDefender=null;lastLogTick=0;
    }
}
