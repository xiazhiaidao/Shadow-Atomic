package local.shadowatomic.client;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import local.shadowatomic.AtomicMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import java.io.IOException;

/** Real player, armor and held-item geometry, flattened through its rendered alpha mask. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class AtomicSilhouette {
    private static ShaderInstance shader;
    private static RenderTarget actor;
    public static boolean ready(){return shader!=null;}
    public static RenderTarget mask(){return actor;}
    @Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void shader(RegisterShadersEvent event)throws IOException{
            event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(AtomicMod.ID,"atomic_actor"),DefaultVertexFormat.POSITION_TEX),s->{shader=s;AtomicMod.LOG.info("ATOMIC_ACTOR_SHADER_READY");});
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){if(actor!=null){actor.destroyBuffers();actor=null;}}
    public static void draw(GuiGraphics parent,boolean dark,boolean lowFlash){
        var mc=Minecraft.getInstance();if(shader==null||mc.player==null)return;
        var main=mc.getMainRenderTarget();
        if(actor==null||actor.width!=main.width||actor.height!=main.height){if(actor!=null)actor.destroyBuffers();actor=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);}
        parent.flush();var player=mc.player;var dispatcher=mc.getEntityRenderDispatcher();
        // Inventory rendering turns the real player to face the camera. Every rotation field,
        // including the *O previous-tick copies the camera interpolates from, must come back or the
        // view swings ~180 degrees for the frames after each silhouette pass.
        float body=player.yBodyRot,yaw=player.getYRot(),pitch=player.getXRot(),head=player.yHeadRot;
        float oldYaw=player.yRotO,oldPitch=player.xRotO,oldBody=player.yBodyRotO,oldHead=player.yHeadRotO;
        Quaternionf orientation=new Quaternionf(dispatcher.cameraOrientation());
        boolean depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST),blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND),cull=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        try{
            actor.setClearColor(0,0,0,0);actor.clear(Minecraft.ON_OSX);actor.bindWrite(true);
            var gui=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
            InventoryScreen.renderEntityInInventoryFollowsAngle(gui,w/2,(int)(h*.88),(int)(h*.34),.48f,.02f,player);
            gui.flush();main.bindWrite(true);
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(()->shader);shader.setSampler("ActorMask",actor.getColorTextureId());
            if(lowFlash)shader.getUniform("Tint").set(.65f,.30f,.95f);
            else shader.getUniform("Tint").set(dark?.015f:.98f,dark?.008f:.97f,dark?.025f:1f);
            var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(1,-1,0).uv(1,0).endVertex();b.vertex(1,1,0).uv(1,1).endVertex();b.vertex(-1,1,0).uv(0,1).endVertex();BufferUploader.drawWithShader(b.end());
        }finally{
            // Inventory rendering temporarily turns the real player. Restore even on exceptions.
            player.yBodyRot=body;player.setYRot(yaw);player.setXRot(pitch);player.yHeadRot=head;
            player.yBodyRotO=oldBody;player.yRotO=oldYaw;player.xRotO=oldPitch;player.yHeadRotO=oldHead;
            dispatcher.overrideCameraOrientation(orientation);dispatcher.setRenderShadow(true);Lighting.setupFor3DItems();
            main.bindWrite(true);RenderSystem.depthMask(true);RenderSystem.setShaderColor(1,1,1,1);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
        }
    }
}
