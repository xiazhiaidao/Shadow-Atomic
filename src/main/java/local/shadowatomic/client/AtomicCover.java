package local.shadowatomic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import local.shadowatomic.AtomicMod;
import org.lwjgl.opengl.GL11;
import java.io.IOException;

/** Brief in-beam transition while the actual return viewpoint warms its visible meshes. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class AtomicCover {
    private static ShaderInstance shader;
    public static boolean available(){return shader!=null;}
    @Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class CoverShaderRegistration {
        @SubscribeEvent public static void coverShaders(RegisterShadersEvent event)throws IOException{
            event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(AtomicMod.ID,"atomic_cover"),DefaultVertexFormat.POSITION_TEX),s->{shader=s;AtomicMod.LOG.info("ATOMIC_COVER_SHADER_READY");});
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(shader==null||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var show=AtomicClient.SHOWS.stream().filter(s->s.coverOpacity()>.001f).findFirst().orElse(null);if(show==null)return;
        var mc=Minecraft.getInstance();mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        try{
            RenderSystem.setShader(()->shader);shader.getUniform("Time").set((float)show.elapsed(0));shader.getUniform("Cover").set(show.coverOpacity());
            shader.getUniform("Aspect").set((float)mc.getWindow().getWidth()/Math.max(1,mc.getWindow().getHeight()));
            var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(1,-1,0).uv(1,0).endVertex();b.vertex(1,1,0).uv(1,1).endVertex();b.vertex(-1,1,0).uv(0,1).endVertex();BufferUploader.drawWithShader(b.end());
        }finally{RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.enableCull();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();}
    }
}
