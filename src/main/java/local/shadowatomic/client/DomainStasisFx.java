package local.shadowatomic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import local.shadowatomic.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.*;

/** Depth-tested, static pause glyphs. A frozen creature never gains a moving animation. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class DomainStasisFx {
    public static int lastDrawn;
    @SubscribeEvent public static void draw(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        lastDrawn=0;var mc=Minecraft.getInstance();if(mc.level==null)return;
        Vec3 camera=event.getCamera().getPosition();
        int limit=AtomicClientConfig.QUALITY.get()==AtomicClientConfig.Quality.LOW?8:24;
        var mobs=DomainStasisClient.entities().stream().filter(e->e.isAlive()&&!e.isInvisible()&&e.distanceToSqr(camera)<48*48&&event.getFrustum().isVisible(e.getBoundingBox().inflate(.5)))
            .sorted(Comparator.comparingDouble(e->e.distanceToSqr(camera))).limit(limit).toList();
        if(mobs.isEmpty())return;
        RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var pose=event.getPoseStack();var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        try{
            for(var mob:mobs){
                Vec3 at=mob.position().subtract(camera);double distance=at.length();
                float alpha=(float)(AtomicMath.smooth(.7,1.8,distance)*(1-AtomicMath.smooth(32,48,distance))*(AtomicClient.lowFlash?.56:.78));
                if(alpha<.01)continue;
                pose.pushPose();
                try{
                    pose.translate(at.x,at.y+.045,at.z);var m=pose.last().pose();double radius=Math.max(.32,mob.getBbWidth()*.72);
                    for(int i=0;i<32;i++){
                        if(i%8>=6)continue;double a=i*Math.PI/16,c=(i+1)*Math.PI/16;
                        vertex(b,m,Math.sin(a)*radius,0,Math.cos(a)*radius,alpha);vertex(b,m,Math.sin(c)*radius,0,Math.cos(c)*radius,alpha);
                        vertex(b,m,Math.sin(c)*(radius+.045),0,Math.cos(c)*(radius+.045),alpha*.25f);vertex(b,m,Math.sin(a)*(radius+.045),0,Math.cos(a)*(radius+.045),alpha*.25f);
                    }
                    pose.translate(0,mob.getBbHeight()+.22,0);pose.mulPose(event.getCamera().rotation());m=pose.last().pose();
                    for(int i=0;i<2;i++){float x=i==0?-.10f:.035f;vertex(b,m,x,0,0,alpha);vertex(b,m,x+.065,.0,0,alpha);vertex(b,m,x+.065,.23,0,alpha);vertex(b,m,x,.23,0,alpha);}
                    lastDrawn++;
                }finally{pose.popPose();}
            }
            BufferUploader.drawWithShader(b.end());
        }finally{RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();}
    }
    private static void vertex(BufferBuilder b,Matrix4f m,double x,double y,double z,float alpha){b.vertex(m,(float)x,(float)y,(float)z).color(.72f,.48f,1f,alpha).endVertex();}
}
