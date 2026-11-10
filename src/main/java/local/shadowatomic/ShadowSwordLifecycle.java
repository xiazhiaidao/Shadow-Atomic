package local.shadowatomic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** World/player NBT owns both the one-time gift and undelivered death returns. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID)
public final class ShadowSwordLifecycle {
    private static final String KEY="ShadowSwordLifecycle",CLAIMED="claimed",PENDING="pending";
    private static CompoundTag state(Player p){return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(KEY);}
    private static void save(Player p,CompoundTag state){
        var root=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        root.put(KEY,state);p.getPersistentData().put(Player.PERSISTED_NBT_TAG,root);
    }
    private static boolean hasSword(Container inventory){
        for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i).is(AtomicMod.SWORD.get()))return true;
        return false;
    }
    /** Never use Inventory.add here: creative mode can silently discard overflow. */
    private static boolean deliver(ServerPlayer p,ItemStack stack){
        int slot=p.getInventory().getFreeSlot();if(slot<0)return false;
        p.getInventory().setItem(slot,stack);p.getInventory().setChanged();p.containerMenu.broadcastChanges();return true;
    }
    private static void refresh(ServerPlayer p,boolean joining){
        if(!p.isAlive()||p.isRemoved()||p.isSpectator())return;
        var state=state(p);var pending=state.getList(PENDING,Tag.TAG_COMPOUND);
        while(!pending.isEmpty()){
            var stack=ItemStack.of(pending.getCompound(0));
            if(!stack.isEmpty()&&!deliver(p,stack))break;
            pending.remove(0);state.put(PENDING,pending);save(p,state);
        }
        if(!pending.isEmpty()||state.getBoolean(CLAIMED)||!AtomicConfig.STARTER_SWORD.get())return;
        if(hasSword(p.getInventory())||hasSword(p.getEnderChestInventory())||p.containerMenu.getCarried().is(AtomicMod.SWORD.get())){
            state.putBoolean(CLAIMED,true);save(p,state);return;
        }
        if(deliver(p,new ItemStack(AtomicMod.SWORD.get()))){
            state.putBoolean(CLAIMED,true);save(p,state);
            p.sendSystemMessage(Component.translatable("message.shadow_atomic.starter_sword"));
        }else if(joining)p.sendSystemMessage(Component.translatable("message.shadow_atomic.starter_full"));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)refresh(p,true);}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)refresh(p,false);}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p&&p.tickCount%20==0)refresh(p,false);
    }
    // Capture only actual drops, after death is accepted and vanilla keepInventory has run.
    // Remove before ordinary grave/drop handlers; no inventory snapshot that can be duplicated.
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
    public static void drops(LivingDropsEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        var state=state(p);var pending=state.getList(PENDING,Tag.TAG_COMPOUND);boolean changed=false;
        var it=e.getDrops().iterator();
        while(it.hasNext()){
            var entity=it.next();var stack=entity.getItem();
            if(stack.is(AtomicMod.SWORD.get())){
                pending.add(stack.save(new CompoundTag()));it.remove();changed=true;
            }
        }
        if(changed){state.putBoolean(CLAIMED,true);state.put(PENDING,pending);save(p,state);}
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){
        var old=e.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if(old.contains(KEY,Tag.TAG_COMPOUND))save(e.getEntity(),old.getCompound(KEY).copy());
    }
}
