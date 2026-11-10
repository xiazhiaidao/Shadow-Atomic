package local.shadowatomic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Per-player, saved independently of the sword stack; the server owns both radii. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID)
public final class ShadowRanges {
    public static final int MIN=10,MAX=200;
    private static final String KEY="ShadowSwordRanges";
    private static final String STACK_DAMAGE="ShadowSwordAttack";
    public static final int DAMAGE_MIN=-1000,DAMAGE_MAX=1000;
    public static int clampDamage(int n){return Math.max(DAMAGE_MIN,Math.min(DAMAGE_MAX,n));}
    public static int damage(ServerPlayer p){var value=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(KEY);return value.contains("damage")?clampDamage(value.getInt("damage")):AtomicMod.SWORD_BASE_DAMAGE;}
    public static int stackDamage(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(STACK_DAMAGE)?clampDamage(stack.getTag().getInt(STACK_DAMAGE)):AtomicMod.SWORD_BASE_DAMAGE;}
    public static float strikeDamage(ServerPlayer p){return p.getMainHandItem().is(AtomicMod.SWORD.get())?damage(p):(float)(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)+AtomicConfig.SHADOW_DAMAGE.get());}
    private static void updateHeld(ServerPlayer p){
        int damage=damage(p);for(var hand:net.minecraft.world.InteractionHand.values()){
            var stack=p.getItemInHand(hand);if(stack.is(AtomicMod.SWORD.get())&&stackDamage(stack)!=damage){stack.getOrCreateTag().putInt(STACK_DAMAGE,damage);p.getInventory().setChanged();}
        }
    }
    public static void setDamage(ServerPlayer p,int damage){
        if(p.isDeadOrDying()||p.isSpectator()||!ShadowGuard.holding(p))return;
        var root=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);var value=root.getCompound(KEY);
        value.putInt("damage",clampDamage(damage));root.put(KEY,value);p.getPersistentData().put(Player.PERSISTED_NBT_TAG,root);updateHeld(p);sync(p);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.START&&e.player instanceof ServerPlayer p)updateHeld(p);}
    public record Scope(Vec3 center,int radius){public boolean contains(Vec3 p){return center.distanceToSqr(p)<=radius*(double)radius;}}
    public static int clamp(int n){return Math.max(MIN,Math.min(MAX,n));}
    public static AtomicNetwork.Ranges get(ServerPlayer p){
        var root=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);var value=root.getCompound(KEY);
        return new AtomicNetwork.Ranges(value.contains("attack")?clamp(value.getInt("attack")):clamp(AtomicConfig.RADIUS.get()),value.contains("recovery")?clamp(value.getInt("recovery")):MAX,damage(p));
    }
    public static void sync(ServerPlayer p){AtomicNetwork.ranges(p,get(p));}
    public static void set(ServerPlayer p,int attack,int recovery){
        if(p.isDeadOrDying()||p.isSpectator()||!ShadowGuard.holding(p))return;
        var root=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);var value=root.getCompound(KEY);
        value.putInt("attack",clamp(attack));value.putInt("recovery",clamp(recovery));root.put(KEY,value);p.getPersistentData().put(Player.PERSISTED_NBT_TAG,root);AtomicDomains.resizeHeld(p,clamp(attack));sync(p);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){var old=e.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);if(old.contains(KEY)){var root=e.getEntity().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);root.put(KEY,old.getCompound(KEY).copy());e.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG,root);}}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
}
