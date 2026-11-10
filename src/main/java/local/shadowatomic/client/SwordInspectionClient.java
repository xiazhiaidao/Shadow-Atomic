package local.shadowatomic.client;

import com.mojang.blaze3d.platform.InputConstants;
import local.shadowatomic.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class SwordInspectionClient {
    public static final KeyMapping INSPECT=new KeyMapping("key.shadow_atomic.inspect",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_H,"key.categories.shadow_atomic");
    public static AtomicNetwork.Ranges ranges=new AtomicNetwork.Ranges(200,200);
    public static long hiddenHudFrames;
    public static void ranges(AtomicNetwork.Ranges value){
        ranges=new AtomicNetwork.Ranges(ShadowRanges.clamp(value.attack()),ShadowRanges.clamp(value.recovery()),ShadowRanges.clampDamage(value.damage()));
        if(Minecraft.getInstance().screen instanceof SwordInspectionScreen screen)screen.authoritative(ranges);
    }
    public static void input(){
        while(AtomicInput.consume(INSPECT)){
            var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null||!ShadowGuard.holding(mc.player)||mc.player.isSpectator())continue;
            if(mc.player.isUsingItem())mc.gameMode.releaseUsingItem(mc.player);
            mc.setScreen(new SwordInspectionScreen());AtomicNetwork.requestRanges();
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){ranges=new AtomicNetwork.Ranges(200,200);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hand(RenderHandEvent e){if(Minecraft.getInstance().screen instanceof SwordInspectionScreen)e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hud(RenderGuiEvent.Pre e){if(Minecraft.getInstance().screen instanceof SwordInspectionScreen){e.setCanceled(true);hiddenHudFrames++;}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.screen instanceof SwordInspectionScreen screen&&(mc.player==null||mc.player.isDeadOrDying()||!ShadowGuard.holding(mc.player)))screen.onClose();
    }
}
