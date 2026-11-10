package local.shadowatomic.client;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import local.shadowatomic.AtomicMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Copies the real framebuffer, preserving scenery contours through the impact frames. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class RecoveryFilter {
    private static ShaderInstance shader;private static RenderTarget scene;
    public static final Set<Integer> drawnStyles=new HashSet<>();public static boolean stateRestored=true;
    public static int style;public static boolean allocated(){return scene!=null;}
    public static void release(){if(scene!=null){int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);scene.destroyBuffers();scene=null;GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);}style=0;}
    @Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void shaders(RegisterShadersEvent e)throws IOException{release();e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(AtomicMod.ID,"recovery_filter"),DefaultVertexFormat.POSITION_TEX),s->{shader=s;AtomicMod.LOG.info("RECOVERY_FILTER_SHADER_READY");});}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void draw(RenderGuiEvent.Post e){
        if(shader==null)return;style=0;
        var show=RecoveryClient.SHOWS.values().stream().filter(s->s.flashAt>=0&&s.age()-s.flashAt<.95&&(s.local()||Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(s.packet.origin())<Math.pow(s.packet.radius()+24,2))).findFirst().orElse(null);
        if(show==null){if(scene!=null)release();return;}
        double t=show.age()-show.flashAt;if(t<0)return;
        float amount;
        if(AtomicClient.lowFlash){style=3;amount=(float)(.18*(1-AtomicMath.smooth(0,.55,t)));}
        else {style=t<.09?1:t<.23?2:3;amount=style<3?1:(float)(1-AtomicMath.smooth(.23,.95,t));}
        if(amount<.001){release();return;}
        e.getGuiGraphics().flush();var previousShader=RenderSystem.getShader();int previousTexture=RenderSystem.getShaderTexture(0);var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();if(main.width<=0||main.height<=0)return;
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        if(scene==null||scene.width!=main.width||scene.height!=main.height){release();scene=new TextureTarget(main.width,main.height,false,Minecraft.ON_OSX);}
        int selectedStyle=AtomicClient.lowFlash?3:t<.09?1:t<.23?2:3;style=selectedStyle;
        try{
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);main.bindWrite(true);
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.setShader(()->shader);
            RenderSystem.setShaderTexture(0,scene.getColorTextureId());shader.setSampler("Sampler0",scene.getColorTextureId());shader.getUniform("Texel").set(1f/main.width,1f/main.height);shader.getUniform("Style").set((float)selectedStyle);shader.getUniform("Amount").set(amount);
            var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(1,-1,0).uv(1,0).endVertex();b.vertex(1,1,0).uv(1,1).endVertex();b.vertex(-1,1,0).uv(0,1).endVertex();BufferUploader.drawWithShader(b.end());drawnStyles.add(selectedStyle);
        }finally{
            RenderSystem.setShader(()->previousShader);RenderSystem.setShaderTexture(0,previousTexture);main.bindWrite(true);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();RenderSystem.depthMask(mask);
            stateRestored&=GL11.glIsEnabled(GL11.GL_DEPTH_TEST)==depth&&GL11.glIsEnabled(GL11.GL_BLEND)==blend&&GL11.glIsEnabled(GL11.GL_CULL_FACE)==cull&&GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)==mask&&RenderSystem.getShader()==previousShader&&RenderSystem.getShaderTexture(0)==previousTexture;
        }
    }
}
