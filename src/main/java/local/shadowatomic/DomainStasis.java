package local.shadowatomic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Skip the entire entity tick, including subclass AI, gravity and timers.
 * Never change NoAI or CanUpdate on the server: autosaves and Recovery keep normal NBT. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID)
public final class DomainStasis {
    private record Frozen(ServerLevel level,Vec3 motion){}
    private static final Map<LivingEntity,Frozen> FROZEN=new IdentityHashMap<>();

    public static boolean frozen(Entity entity){return FROZEN.containsKey(entity);}
    private static boolean covered(Entity entity){
        return entity instanceof LivingEntity living&&!(entity instanceof Player)&&living.isAlive()
            &&entity.level() instanceof ServerLevel&&AtomicDomains.contains(living);
    }
    private static void sync(LivingEntity entity,boolean frozen){AtomicNetwork.stasis(entity,new AtomicNetwork.Stasis(entity.getId(),entity.getUUID(),frozen));}
    private static boolean skip(Entity entity){
        if(!covered(entity)){
            if(entity instanceof LivingEntity living)thaw(living);
            return false;
        }
        var living=(LivingEntity)entity;
        if(!FROZEN.containsKey(living)){
            FROZEN.put(living,new Frozen((ServerLevel)living.level(),living.getDeltaMovement()));
            sync(living,true);
        }
        // Vanilla increments this before calling tick/rideTick. Hold animation age too.
        entity.tickCount--;
        return true;
    }
    /** Called by the Forge transformer at ServerLevel's normal tick invocation. */
    public static void tickEntity(Entity entity){if(!skip(entity))entity.tick();}
    /** Passengers still get traversed; players riding a frozen mob remain playable. */
    public static void tickPassenger(Entity entity){if(!skip(entity))entity.rideTick();}
    private static void thaw(LivingEntity entity){
        Frozen state=FROZEN.remove(entity);if(state==null)return;
        entity.setDeltaMovement(state.motion());
        if(!entity.isRemoved())sync(entity,false);
    }
    public static void refresh(){
        for(var entity:new ArrayList<>(FROZEN.keySet()))if(entity.isRemoved()||FROZEN.get(entity).level()!=entity.level()||!covered(entity))thaw(entity);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){
        if(e.getEntity() instanceof ServerPlayer player&&frozen(e.getTarget()))
            AtomicNetwork.stasis(player,new AtomicNetwork.Stasis(e.getTarget().getId(),e.getTarget().getUUID(),true));
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent e){if(!e.getLevel().isClientSide()&&e.getEntity() instanceof LivingEntity living)thaw(living);}
    @SubscribeEvent public static void stopping(ServerStoppingEvent e){for(var entity:new ArrayList<>(FROZEN.keySet()))thaw(entity);}
}
