package local.shadowatomic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Held-sword physical abilities. Vanilla movement retains collision and server validation. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID)
public final class ShadowPowers {
    private static final String SAVED="ShadowAtomicFlightLease";
    public static final UUID SPEED=UUID.fromString("e7488bc3-43a4-4d1c-8822-8206b6847121");
    public static final UUID ATTACK=UUID.fromString("e7488bc3-43a4-4d1c-8822-8206b6847122");
    public static final UUID STEP=UUID.fromString("e7488bc3-43a4-4d1c-8822-8206b6847123");
    public static final UUID RESIST=UUID.fromString("e7488bc3-43a4-4d1c-8822-8206b6847124");
    private static final Map<UUID,ServerPlayer> ACTIVE=new HashMap<>();
    private static final Map<UUID,Long> TOGGLED=new HashMap<>();
    public static boolean eligible(ServerPlayer p){
        return AtomicConfig.SHADOW_POWERS.get()&&!p.isSpectator()&&!p.isDeadOrDying()&&!p.isRemoved()&&ShadowGuard.holding(p);
    }
    private static void modifier(ServerPlayer p,Attribute attribute,UUID id,double amount,AttributeModifier.Operation operation){
        var a=p.getAttribute(attribute);if(a==null)return;var old=a.getModifier(id);
        if(old!=null&&old.getAmount()==amount&&old.getOperation()==operation)return;
        a.removeModifier(id);a.addTransientModifier(new AttributeModifier(id,"Shadow body",amount,operation));
    }
    private static void remove(ServerPlayer p,Attribute a,UUID id){var value=p.getAttribute(a);if(value!=null)value.removeModifier(id);}
    public static void refresh(ServerPlayer p){
        if(!eligible(p)){release(p);return;}
        var lease=p.getPersistentData().getCompound(SAVED);
        if(lease.contains("mode")&&!lease.getString("mode").equals(p.gameMode.getGameModeForPlayer().getName()))release(p);
        if(ACTIVE.get(p.getUUID())!=p){
            var old=ACTIVE.remove(p.getUUID());if(old!=null)release(old);
            restoreFlight(p);var a=p.getAbilities();var before=new CompoundTag();
            before.putBoolean("mayfly",a.mayfly);before.putBoolean("flying",a.flying);before.putFloat("speed",a.getFlyingSpeed());
            before.putString("mode",p.gameMode.getGameModeForPlayer().getName());
            p.getPersistentData().put(SAVED,before);ACTIVE.put(p.getUUID(),p);a.mayfly=true;
            p.onUpdateAbilities();sync(p,true);
        }
        if(p.isCreative()){
            remove(p,Attributes.MOVEMENT_SPEED,SPEED);remove(p,Attributes.ATTACK_SPEED,ATTACK);remove(p,ForgeMod.STEP_HEIGHT_ADDITION.get(),STEP);remove(p,Attributes.KNOCKBACK_RESISTANCE,RESIST);
        }else{
            modifier(p,Attributes.MOVEMENT_SPEED,SPEED,AtomicConfig.SHADOW_SPEED.get()-1,AttributeModifier.Operation.MULTIPLY_TOTAL);
            modifier(p,Attributes.ATTACK_SPEED,ATTACK,1.6,AttributeModifier.Operation.ADDITION);
            modifier(p,ForgeMod.STEP_HEIGHT_ADDITION.get(),STEP,.6,AttributeModifier.Operation.ADDITION);
            modifier(p,Attributes.KNOCKBACK_RESISTANCE,RESIST,1,AttributeModifier.Operation.ADDITION);
        }
        if(!p.getAbilities().mayfly){p.getAbilities().mayfly=true;p.onUpdateAbilities();}
        // Keep server movement allowance in step with deliberately sprinting flight.
        // The client ramps the granted speed; vanilla travel retains WASD and collision.
        float speed=(float)(p.getAbilities().flying&&p.isSprinting()?Math.max(AtomicConfig.SHADOW_FLY.get(),AtomicConfig.SHADOW_BOOST.get()):AtomicConfig.SHADOW_FLY.get());
        if(p.getAbilities().getFlyingSpeed()!=speed){p.getAbilities().setFlyingSpeed(speed);p.onUpdateAbilities();}
        if(p.getAbilities().flying)p.fallDistance=0;
        if(p.server.getTickCount()%40==0)sync(p,true);
    }
    private static void sync(ServerPlayer p,boolean active){AtomicNetwork.powers(p,new AtomicNetwork.Powers(active,AtomicConfig.SHADOW_FLY.get().floatValue(),(float)Math.max(AtomicConfig.SHADOW_FLY.get(),AtomicConfig.SHADOW_BOOST.get()),AtomicConfig.SHADOW_JUMP.get().floatValue()));}
    private static void restoreFlight(ServerPlayer p){
        var data=p.getPersistentData();if(!data.contains(SAVED))return;var before=data.getCompound(SAVED);var a=p.getAbilities();
        // A game-mode change has already granted its own permissions.
        boolean sameMode=!before.contains("mode")||before.getString("mode").equals(p.gameMode.getGameModeForPlayer().getName());
        if(!p.isCreative()&&!p.isSpectator()&&sameMode){a.mayfly=before.getBoolean("mayfly");a.flying=before.getBoolean("flying")&&a.mayfly;}
        a.setFlyingSpeed(before.getFloat("speed"));data.remove(SAVED);p.fallDistance=0;p.onUpdateAbilities();
    }
    public static void release(ServerPlayer p){
        boolean owned=ACTIVE.get(p.getUUID())==p;
        if(owned)ACTIVE.remove(p.getUUID());
        remove(p,Attributes.MOVEMENT_SPEED,SPEED);remove(p,Attributes.ATTACK_SPEED,ATTACK);
        remove(p,ForgeMod.STEP_HEIGHT_ADDITION.get(),STEP);remove(p,Attributes.KNOCKBACK_RESISTANCE,RESIST);
        restoreFlight(p);if(owned)sync(p,false);
    }
    public static void toggleFlight(ServerPlayer p){
        if(!eligible(p))return;long now=p.server.getTickCount();
        if(TOGGLED.getOrDefault(p.getUUID(),-100L)+4>now)return;TOGGLED.put(p.getUUID(),now);
        refresh(p);var a=p.getAbilities();a.flying=!a.flying;p.fallDistance=0;
        if(a.flying&&p.onGround()){
            p.setDeltaMovement(p.getDeltaMovement().add(0,.42,0));p.setOnGround(false);
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
        }
        p.onUpdateAbilities();p.displayClientMessage(Component.translatable(a.flying?"message.shadow_atomic.flight_on":"message.shadow_atomic.flight_off"),true);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.START&&e.player instanceof ServerPlayer p)refresh(p);}
    @SubscribeEvent public static void jump(LivingJumpEvent e){if(e.getEntity() instanceof ServerPlayer p&&eligible(p)&&!p.isCreative()&&!p.getAbilities().flying)p.setDeltaMovement(p.getDeltaMovement().multiply(1,AtomicConfig.SHADOW_JUMP.get(),1));}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void attack(AttackEntityEvent e){
        // Signed sword values also apply when enhanced mobility is disabled. Cancel vanilla's
        // entire attack route first, including enchantment/sweep damage and invalid targets.
        if(e.getEntity() instanceof ServerPlayer healer&&healer.getMainHandItem().is(AtomicMod.SWORD.get())&&ShadowRanges.damage(healer)<=0){
            e.setCanceled(true);
            if(healer.isDeadOrDying()||healer.isSpectator()||!(e.getTarget() instanceof LivingEntity target)||!target.isAlive())return;
            double reach=healer.getAttributeValue(ForgeMod.ENTITY_REACH.get());
            if(healer.distanceToSqr(target)>reach*reach||!healer.hasLineOfSight(target)||healer.getAttackStrengthScale(.5f)<.9f)return;
            healer.resetAttackStrengthTicker();float amount=-ShadowRanges.damage(healer);
            if(amount>0)target.heal(amount); // Forge LivingHealEvent and max-health cap remain authoritative.
            return;
        }
        if(!(e.getEntity() instanceof ServerPlayer p)||!eligible(p)||!AtomicConfig.mode().damagesEntities()||!(e.getTarget() instanceof LivingEntity target)||!PlanetaryAtomic.validTarget(p,target))return;
        // Cancel the ordinary attack only for this empowered single-target route; no sweeping secondary victims.
        e.setCanceled(true);
        double reach=p.getAttributeValue(ForgeMod.ENTITY_REACH.get());
        if(p.distanceToSqr(target)>reach*reach||!p.hasLineOfSight(target)||p.getAttackStrengthScale(.5f)<.9f)return;
        float damage=ShadowRanges.strikeDamage(p);
        try{
            p.resetAttackStrengthTicker();
            if(AtomicServer.shadowStrike(p,UUID.randomUUID(),target,damage)){
                p.crit(target);p.getMainHandItem().hurtAndBreak(1,p,x->x.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
            }
        }catch(Exception error){AtomicMod.LOG.error("SHADOW_STRIKE_ABORT recovery record failure",error);p.displayClientMessage(Component.translatable("message.shadow_atomic.strike_failed"),true);}
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){restoreFlight(p);refresh(p);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){release(p);TOGGLED.remove(p.getUUID());}}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){release(p);TOGGLED.remove(p.getUUID());}}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)release(p);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){ACTIVE.clear();TOGGLED.clear();}
}
