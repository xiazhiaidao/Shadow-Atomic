package local.shadowatomic.client;

import com.mojang.blaze3d.pipeline.*;
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
import local.shadowatomic.AtomicClientConfig;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import java.io.IOException;

/** Depth-aware original volume shader, independent from external shader packs. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class AtomicVolume {
    private static ShaderInstance shader,bloom;
    private static RenderTarget scene,energy,blur;
    public static boolean ready(){return shader!=null&&AtomicClientConfig.ENHANCED_LIGHTING.get()&&Minecraft.getInstance().options.graphicsMode().get()!=net.minecraft.client.GraphicsStatus.FABULOUS;}
    public static boolean forShow(AtomicClient.Show show,net.minecraft.world.phys.Vec3 camera){
        if(!ready())return false;double distance=show.origin(1).distanceToSqr(camera);if(distance>1200*1200)return false;
        int rank=0,index=AtomicClient.SHOWS.indexOf(show);
        for(var other:AtomicClient.SHOWS){
            if(other==show||other.age(0)>15)continue;double d=other.origin(1).distanceToSqr(camera);if(d>1200*1200)continue;
            if(other.local()&&!show.local()||other.local()==show.local()&&(d<distance||d==distance&&AtomicClient.SHOWS.indexOf(other)<index))rank++;
        }
        return rank<AtomicClientConfig.QUALITY.get().volumeEvents;
    }
    /**
     * Registered explicitly from the mod constructor. The automatic {@code @EventBusSubscriber}
     * scan stopped calling this nested listener after 0.12.0 (no entry log, no exception), which
     * silently removed the volumetric pass from the first skill; an explicit listener keeps the
     * depth-aware volume and bloom alive regardless of scanning order.
     */
    public static void attach(net.minecraftforge.eventbus.api.IEventBus bus){bus.addListener(AtomicVolume::shaders);}
    private static void shaders(RegisterShadersEvent event){
        try{
            event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(AtomicMod.ID,"atomic_volume"),DefaultVertexFormat.POSITION_TEX),s->{shader=s;AtomicMod.LOG.info("ATOMIC_VOLUME_SHADER_READY");});
            event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(AtomicMod.ID,"atomic_bloom"),DefaultVertexFormat.POSITION_TEX),s->{bloom=s;AtomicMod.LOG.info("ATOMIC_BLOOM_SHADER_READY");});
        }catch(Exception error){
            shader=null;bloom=null;
            AtomicMod.LOG.error("ATOMIC_VOLUME_SHADER_FAIL {}",error.toString(),error);
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){releaseTargets();}
    private static void releaseTargets(){for(RenderTarget target:new RenderTarget[]{scene,energy,blur})if(target!=null)target.destroyBuffers();scene=null;energy=null;blur=null;}
    private static void quad(){var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(1,-1,0).uv(1,0).endVertex();b.vertex(1,1,0).uv(1,1).endVertex();b.vertex(-1,1,0).uv(0,1).endVertex();BufferUploader.drawWithShader(b.end());}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        if(!ready()||AtomicClient.SHOWS.isEmpty()){if(scene!=null)releaseTargets();return;}
        var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();
        if(main.width<=0||main.height<=0)return;
        var quality=AtomicClientConfig.QUALITY.get();int divisor=quality.bloomDivisor;
        int bw=Math.max(1,main.width/divisor),bh=Math.max(1,main.height/divisor);
        if(scene==null||scene.width!=main.width||scene.height!=main.height||blur.width!=bw||blur.height!=bh){releaseTargets();scene=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);energy=new TextureTarget(main.width,main.height,false,Minecraft.ON_OSX);blur=new TextureTarget(bw,bh,false,Minecraft.ON_OSX);blur.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);}
        // Nearest event first, with a bounded number of full-screen composites.
        var shows=AtomicClient.SHOWS.stream().filter(s->forShow(s,event.getCamera().getPosition())).toList();
        for(var show:shows){
            float age=(float)show.age(event.getPartialTick());if(age<0||age>15)continue;
            var cam=event.getCamera().getPosition().subtract(show.origin(event.getPartialTick()));if(cam.lengthSqr()>1200*1200)continue;
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);
            GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL30.GL_COLOR_BUFFER_BIT|GL30.GL_DEPTH_BUFFER_BIT,GL30.GL_NEAREST);
            boolean glow=bloom!=null&&age>=7&&age<14.8;
            if(glow)energy.bindWrite(true);else main.bindWrite(true);
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();
            try{
                RenderSystem.setShader(()->shader);
                shader.setSampler("SceneColor",scene.getColorTextureId());shader.setSampler("SceneDepth",scene.getDepthTextureId());
                shader.getUniform("InvProjection").set(new Matrix4f(event.getProjectionMatrix()).invert());
                // AFTER_LEVEL may have a restored pose stack. Camera basis is authoritative;
                // Minecraft camera forward is +Z while projection-space forward is -Z.
                shader.getUniform("InvView").set(new Matrix4f().rotation(event.getCamera().rotation()).rotateY((float)Math.PI));
                shader.getUniform("CameraLocal").set((float)cam.x,(float)cam.y,(float)cam.z);
                shader.getUniform("Time").set(age);shader.getUniform("Phase").set(show.packet.releasing()?1f:0f);shader.getUniform("Scale").set(show.packet.scale());
                shader.getUniform("FlowTime").set((float)show.elapsed(event.getPartialTick()));
                shader.getUniform("LowFlash").set(AtomicClient.lowFlash?1f:0f);
                shader.getUniform("VolumeSamples").set((float)quality.opticalSamples);
                shader.getUniform("ScreenSize").set((float)main.width,(float)main.height);
                quad();
                if(glow){
                    // Two separable passes spread actual bright pixels. The energy silhouette
                    // stays sharp; only emitted light receives the small blur.
                    blur.bindWrite(true);RenderSystem.setShader(()->bloom);
                    bloom.setSampler("SceneColor",energy.getColorTextureId());bloom.setSampler("BloomColor",scene.getColorTextureId());
                    bloom.getUniform("Direction").set((float)divisor/main.width,0f);bloom.getUniform("Pass").set(0f);quad();
                    main.bindWrite(true);RenderSystem.setShader(()->bloom);
                    bloom.setSampler("SceneColor",energy.getColorTextureId());bloom.setSampler("BloomColor",blur.getColorTextureId());
                    bloom.getUniform("Direction").set(0f,1f/blur.height);bloom.getUniform("Pass").set(1f);quad();
                }
            }finally{main.bindWrite(true);RenderSystem.enableCull();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.defaultBlendFunc();}
        }
    }
}
