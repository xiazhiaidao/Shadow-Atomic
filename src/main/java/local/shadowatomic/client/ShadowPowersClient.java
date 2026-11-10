package local.shadowatomic.client;

import com.mojang.blaze3d.platform.InputConstants;
import local.shadowatomic.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Local movement prediction uses only the server's granted abilities and speed limits. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class ShadowPowersClient {
    public static final KeyMapping FLIGHT=new KeyMapping("key.shadow_atomic.flight",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_G,"key.categories.shadow_atomic");
    public static AtomicNetwork.Powers state=new AtomicNetwork.Powers(false,.05f,.05f,1);
    public static void state(AtomicNetwork.Powers value){state=value;}
    public static void clear(){state=new AtomicNetwork.Powers(false,.05f,.05f,1);}
    public static void input(){while(AtomicInput.consume(FLIGHT))AtomicNetwork.toggleFlight();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.START)return;var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null){clear();return;}
        if(mc.isPaused()||!state.active()||!ShadowGuard.holding(mc.player)||mc.player.isSpectator())return;
        if(!mc.player.getAbilities().flying)mc.player.getAbilities().setFlyingSpeed(state.normal());
    }
    @SubscribeEvent public static void fov(ComputeFovModifierEvent e){
        if(state.active()&&ShadowGuard.holding(e.getPlayer())&&!e.getPlayer().getAbilities().flying)e.setNewFovModifier(Math.min(e.getNewFovModifier(),1.12f));
    }
    @SubscribeEvent public static void jump(LivingJumpEvent e){
        var mc=Minecraft.getInstance();
        if(e.getEntity()==mc.player&&state.active()&&ShadowGuard.holding(mc.player)&&!mc.player.isCreative()&&!mc.player.getAbilities().flying)
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(1,state.jump(),1));
    }
}
