package local.shadowatomic.client;

import com.mojang.blaze3d.platform.InputConstants;
import local.shadowatomic.AtomicMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Physical press edges, independent from OS repeat and vanilla held-item auto-use. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class AtomicInput {
    private static final Map<KeyMapping,Integer> pending=new IdentityHashMap<>(),ready=new IdentityHashMap<>();
    private static boolean swordUseConsumed;
    public static KeyMapping[] bindings(){return new KeyMapping[]{AtomicClient.SKILL,AtomicClient.PLANETARY,AtomicClient.RECOVERY,AtomicClient.REPLAY,AtomicClient.CAMERA,AtomicClient.FLASH,AtomicClient.QUALITY,ShadowPowersClient.FLIGHT,SwordInspectionClient.INSPECT};}
    /** Register on the actual mod bus; nested subscriber scanning omitted these from Options.keyMappings. */
    public static void attach(net.minecraftforge.eventbus.api.IEventBus bus){bus.addListener(AtomicInput::registerKeys);}
    private static void registerKeys(net.minecraftforge.client.event.RegisterKeyMappingsEvent event){
        for(var binding:bindings())event.register(binding);
        AtomicMod.LOG.info("ATOMIC_KEYS_REGISTERED count={}",bindings().length);
    }
    private static void press(InputConstants.Key key){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.screen!=null||mc.getOverlay()!=null)return;
        for(var binding:bindings())if(binding.isActiveAndMatches(key))pending.merge(binding,1,(a,b)->Math.min(8,a+b));
    }
    @SubscribeEvent public static void keyboard(InputEvent.Key event){
        if(event.getAction()==GLFW.GLFW_PRESS)press(InputConstants.getKey(event.getKey(),event.getScanCode()));
        else if(event.getAction()==GLFW.GLFW_RELEASE){if(Minecraft.getInstance().options.keyUse.matches(event.getKey(),event.getScanCode()))swordUseConsumed=false;}
    }
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Post event){
        if(event.getAction()==GLFW.GLFW_PRESS)press(InputConstants.Type.MOUSE.getOrCreate(event.getButton()));
        else if(event.getAction()==GLFW.GLFW_RELEASE){if(Minecraft.getInstance().options.keyUse.matchesMouse(event.getButton()))swordUseConsumed=false;}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void use(InputEvent.InteractionKeyMappingTriggered event){
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        if(!event.isUseItem()||!mc.player.getItemInHand(event.getHand()).is(AtomicMod.SWORD.get()))return;
        if(swordUseConsumed){event.setCanceled(true);event.setSwingHand(false);}else swordUseConsumed=true;
    }
    public static void tick(){
        if(!Minecraft.getInstance().options.keyUse.isDown())swordUseConsumed=false;
        for(var binding:bindings()){
            boolean vanilla=false;while(binding.consumeClick())vanilla=true;
            int count=pending.getOrDefault(binding,0);pending.remove(binding);
            ready.put(binding,vanilla?count:0);
        }
    }
    public static boolean consume(KeyMapping binding){int count=ready.getOrDefault(binding,0);if(count<=0)return false;ready.put(binding,count-1);return true;}
    public static void clear(){pending.clear();ready.clear();for(var binding:bindings())while(binding.consumeClick()){}swordUseConsumed=Minecraft.getInstance().options.keyUse.isDown();}
}
