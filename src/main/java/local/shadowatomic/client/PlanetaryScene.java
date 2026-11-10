package local.shadowatomic.client;

import local.shadowatomic.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Original analytic 3D macro scene; no world teleport, distant chunks, or reference-mod assets. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class PlanetaryScene {
    private static ShaderInstance shader;
    private static final Set<Integer> mipmapped=new HashSet<>();
    private static final ResourceLocation DAY=new ResourceLocation(AtomicMod.ID,"textures/planet/earth_day.png"),NIGHT=new ResourceLocation(AtomicMod.ID,"textures/planet/earth_night.png");
    public static boolean available(){return shader!=null;}
    @Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class PlanetaryShaders {
        @SubscribeEvent public static void registerPlanetaryShaders(RegisterShadersEvent event)throws IOException{
            mipmapped.clear();
            event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(AtomicMod.ID,"planetary"),DefaultVertexFormat.POSITION_TEX),s->{shader=s;AtomicMod.LOG.info("PLANETARY_SHADER_READY");});
        }
    }
    private static void planetTexture(int id){
        if(mipmapped.add(id)){
            // SimpleTexture has only the base level. Distant Earth needs filtered levels,
            // otherwise the 2048px continents and city lights sparkle at 70px diameter.
            RenderSystem.bindTexture(id);GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||shader==null)return;
        var show=PlanetaryClient.own();if(show==null||show.opacity()<.001)return;var mc=Minecraft.getInstance();mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        try{
            RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(0,DAY);RenderSystem.setShaderTexture(1,NIGHT);
            planetTexture(RenderSystem.getShaderTexture(0));planetTexture(RenderSystem.getShaderTexture(1));
            shader.setSampler("EarthDay",RenderSystem.getShaderTexture(0));shader.setSampler("EarthNight",RenderSystem.getShaderTexture(1));
            shader.getUniform("Time").set((float)show.age());shader.getUniform("Opacity").set(show.opacity());shader.getUniform("Aspect").set((float)mc.getWindow().getWidth()/Math.max(1,mc.getWindow().getHeight()));
            shader.getUniform("LowFlash").set(AtomicClient.lowFlash?1f:0f);shader.getUniform("Detail").set(AtomicClientConfig.QUALITY.get()==AtomicClientConfig.Quality.LOW?0f:1f);
            shader.getUniform("Focal").set((float)(.5/Math.tan(Math.toRadians(PlanetaryPortrait.fov(show.age())*.5))));
            var touchEye=PlanetaryTouch.eye(show.age());var touchLook=PlanetaryTouch.look();var touchNormal=PlanetaryTouch.normal();
            shader.getUniform("TouchEye").set(touchEye.x,touchEye.y,touchEye.z);shader.getUniform("TouchLook").set(touchLook.x,touchLook.y,touchLook.z);
            shader.getUniform("TouchNormal").set(touchNormal.x,touchNormal.y,touchNormal.z);
            shader.getUniform("TouchShot").set(PlanetaryTouch.active(show.age())?1f:0f);shader.getUniform("ShieldProgress").set(PlanetaryTouch.progress(show.age()));
            var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(1,-1,0).uv(1,0).endVertex();b.vertex(1,1,0).uv(1,1).endVertex();b.vertex(-1,1,0).uv(0,1).endVertex();BufferUploader.drawWithShader(b.end());
        }finally{RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();}
        PlanetaryPortrait.draw(show);
    }
}
