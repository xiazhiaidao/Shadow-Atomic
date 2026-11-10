package local.shadowatomic.client;

import local.shadowatomic.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Tracking packets are independent of the four-show visual cap and local cameras. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class DomainStasisClient {
    private record Frozen(boolean canUpdate,int ticks){}
    private static final Map<Integer,UUID> REQUESTED=new HashMap<>();
    private static final Map<LivingEntity,Frozen> FROZEN=new IdentityHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static boolean rendering;
    public static boolean frozen(LivingEntity entity){return FROZEN.containsKey(entity);}
    public static List<LivingEntity> entities(){return List.copyOf(FROZEN.keySet());}
    public static long nearbyCount(){var p=Minecraft.getInstance().player;return p==null?0:FROZEN.keySet().stream().filter(e->e.isAlive()&&e.distanceToSqr(p)<48*48).count();}
    public static void state(AtomicNetwork.Stasis packet){
        var current=Minecraft.getInstance().level;
        if(current!=level){clear();level=current;}
        if(packet.frozen())REQUESTED.put(packet.entity(),packet.uuid());
        else{
            REQUESTED.remove(packet.entity(),packet.uuid());
            for(var entity:new ArrayList<>(FROZEN.keySet()))if(entity.getId()==packet.entity()&&entity.getUUID().equals(packet.uuid()))thaw(entity);
        }
        apply();
    }
    private static void thaw(LivingEntity entity){var state=FROZEN.remove(entity);if(state!=null)entity.canUpdate(state.canUpdate());}
    public static void clear(){for(var entity:new ArrayList<>(FROZEN.keySet()))thaw(entity);REQUESTED.clear();level=null;}
    private static void apply(){
        if(level==null)return;
        for(var entry:REQUESTED.entrySet()){
            var target=level.getEntity(entry.getKey());
            if(target instanceof LivingEntity living&&!(living instanceof Player)&&living.isAlive()&&living.getUUID().equals(entry.getValue()))
                FROZEN.computeIfAbsent(living,e->new Frozen(e.canUpdate(),e.tickCount));
        }
        for(var entity:new ArrayList<>(FROZEN.keySet())){
            if(entity.isRemoved()||!entity.isAlive()||entity.level()!=level||!entity.getUUID().equals(REQUESTED.get(entity.getId()))){thaw(entity);continue;}
            entity.canUpdate(false);entity.tickCount=FROZEN.get(entity).ticks();
            entity.setOldPosAndRot();entity.yHeadRotO=entity.yHeadRot;entity.yBodyRotO=entity.yBodyRot;
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        var current=Minecraft.getInstance().level;
        if(current!=level){clear();level=current;}
        apply();
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent e){
        if(e.getLevel().isClientSide()&&e.getEntity() instanceof LivingEntity living){thaw(living);REQUESTED.remove(living.getId(),living.getUUID());}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
    @SuppressWarnings("unchecked")
    @SubscribeEvent public static <T extends LivingEntity,M extends net.minecraft.client.model.EntityModel<T>> void render(RenderLivingEvent.Pre<T,M> e){
        if(rendering||!frozen(e.getEntity()))return;
        e.setCanceled(true);rendering=true;
        try{e.getRenderer().render((T)e.getEntity(),e.getEntity().getYRot(),0,e.getPoseStack(),e.getMultiBufferSource(),e.getPackedLight());}
        finally{rendering=false;}
    }
}
