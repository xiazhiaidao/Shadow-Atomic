package local.shadowatomic.client;

import local.shadowatomic.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/** Real-world protection rise and selected-target glyphs, visible without the macro camera. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class PlanetaryWorld {
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||PlanetaryClient.SHOWS.isEmpty())return;
        var pose=event.getPoseStack();var cam=event.getCamera().getPosition();
        RenderSystem.enableBlend();RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try{
            for(var show:PlanetaryClient.SHOWS.values()){
                double t=show.age();if(t<0||t>20||cam.distanceToSqr(show.packet.origin())>900*900)continue;
                pose.pushPose();Vec3 origin=show.packet.origin();pose.translate(origin.x-cam.x,origin.y-cam.y+.04,origin.z-cam.z);
                try{
                    var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);var m=pose.last().pose();
                    float fade=(float)(1-AtomicMath.smooth(17,20,t));double grow=AtomicMath.smooth(0,2.2,t);
                    ring(b,m,Vec3.ZERO,5+28*grow,.035,0,.4f*fade,0);
                    // Only a few broad moving arcs imply a curved shield; no grid cage.
                    for(int axis=0;axis<3;axis++)ring(b,m,new Vec3(0,1,0),3+9*grow,.027,axis,.23f*fade,t*.10+axis*.7);
                    var creature=net.minecraft.client.Minecraft.getInstance().level.getEntity(show.packet.target());
                    // Keep the world glyph on the same UUID captured by the server, never a new nearby mob.
                    boolean same=creature!=null&&show.packet.look()!=null&&creature.getUUID().equals(show.packet.look().uuid());
                    Vec3 targetPosition=same?creature.getPosition(event.getPartialTick()):show.packet.targetPos();
                    float height=same?creature.getBbHeight():1.8f,width=same?creature.getBbWidth():.8f;
                    Vec3 target=targetPosition.subtract(origin).add(0,height*.52,0);
                    if(t>=.4&&t<13.4){
                        Vector3f forward=targetPosition.add(0,height*.52,0).subtract(cam).normalize().toVector3f();
                        Vector3f right=new Vector3f(forward).cross(0,1,0);if(right.lengthSquared()<.001)right.set(1,0,0);else right.normalize();
                        Vector3f up=new Vector3f(right).cross(forward).normalize();float extent=Math.max(height,width);
                        Vector3f center=target.toVector3f().fma(extent*.65f,forward);
                        PlanetaryLock.geometry(b,m,center,right,up,extent,(float)AtomicMath.smooth(.4,1.2,t)*.7f*fade,t);
                        ribbon(b,m,new Vec3(0,1.5,0),target,.018,.25f*fade);
                    }
                    if(t>=13.4){
                        double u=t-13.4;float a=(float)(Math.exp(-u*1.5)*.9);ribbon(b,m,target.add(0,-1,0),target.add(0,400,0),1.5,a);
                        for(int i=0;i<3;i++){double f=AtomicMath.clamp((u-i*.16)/2.5);if(f>0&&f<1)ring(b,m,target,3+150*f,.06+i*.03,0,(float)((1-f)*.6),0);}
                    }
                    BufferUploader.drawWithShader(b.end());
                }finally{pose.popPose();}
            }
        }finally{RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();}
    }
    private static void vertex(BufferBuilder b,Matrix4f m,Vec3 p,float a){b.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(.6f,.12f,1f,a).endVertex();}
    private static void ribbon(BufferBuilder b,Matrix4f m,Vec3 a,Vec3 c,double width,float alpha){
        Vec3 axis=c.subtract(a).normalize(),side=axis.cross(new Vec3(0,1,0));if(side.lengthSqr()<.01)side=new Vec3(1,0,0);side=side.normalize().scale(width);
        vertex(b,m,a.subtract(side),alpha);vertex(b,m,a.add(side),alpha);vertex(b,m,c.add(side),alpha);vertex(b,m,c.subtract(side),alpha);
    }
    private static void ring(BufferBuilder b,Matrix4f m,Vec3 center,double radius,double width,int axis,float alpha,double phase){
        for(int i=0;i<128;i++){
            double a=i*Math.PI/64+phase,c=(i+1)*Math.PI/64+phase;
            if(axis>0&&i%32>24)continue;
            Vec3 p=point(a,radius,axis).add(center),q=point(c,radius,axis).add(center);ribbon(b,m,p,q,width,alpha);
        }
    }
    private static Vec3 point(double a,double r,int axis){double x=Math.cos(a)*r,y=Math.sin(a)*r;return axis==0?new Vec3(x,0,y):axis==1?new Vec3(x,y,0):new Vec3(0,y,x);}
}
