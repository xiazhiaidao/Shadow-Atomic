package local.shadowatomic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import local.shadowatomic.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.Random;

/** Gold filaments and violet-white outward fronts, drawn in the real world without camera takeover. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class RecoveryRenderer {
    public static int drawnFrames;
    @SubscribeEvent public static void world(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||RecoveryClient.SHOWS.isEmpty())return;
        var pose=e.getPoseStack();var camera=e.getCamera().getPosition();RenderSystem.enableBlend();RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try{for(var s:RecoveryClient.SHOWS.values()){
            var o=s.packet.origin();double age=s.age(),fade=s.doneAt<0?1:1-AtomicMath.smooth(0,2.5,age-s.doneAt);if(fade<=0||o.distanceToSqr(camera)>600*600)continue;
            pose.pushPose();pose.translate(o.x-camera.x,o.y-camera.y+.1,o.z-camera.z);try{
                var m=pose.last().pose();var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
                double radius=Math.max(12,s.packet.radius()),grow=AtomicMath.smooth(.2,5.5,age),front=radius*grow;
                // Finite moving fronts have transparent interiors; the terrain remains readable.
                ring(b,m,front,0,.25+radius*.008,.66,.48,1,.65*fade);
                ring(b,m,front*.96,.08,.08+radius*.002,1,.85,.45,.6*fade);
                for(int i=0;i<3;i++){
                    double cycle=(age*.21+i*.33)%1,r=radius*cycle,alpha=.17*Math.sin(Math.PI*cycle)*fade*grow;
                    ring(b,m,r,.03,.09+radius*.003,.74,.6,1,alpha);
                }
                Random random=new Random(s.packet.event().getLeastSignificantBits());int count=AtomicClientConfig.QUALITY.get()==AtomicClientConfig.Quality.LOW?32:96;
                for(int i=0;i<count;i++){
                    double angle=random.nextDouble()*Math.PI*2,rr=(.12+random.nextDouble()*.88)*front,y=1+random.nextDouble()*radius*.18;
                    double phase=(age*.32+random.nextDouble())%1;rr*=1-phase*.07;y*=1-phase;
                    Vec3 p=new Vec3(Math.sin(angle)*rr,y,Math.cos(angle)*rr);ribbon(b,m,p,p.add(0,.2+phase*.7,0),.025+radius*.0004,1,.84,.4,.5*fade*Math.sin(Math.PI*phase));
                }
                // Brief filaments converge onto the actual positions supplied by the server.
                for(var spark:s.sparks){double t=AtomicMath.clamp((age-spark.start())/1.5);Random r=new Random(spark.seed());Vec3 target=spark.target().subtract(o);Vec3 offset=new Vec3((r.nextDouble()-.5)*3,3+r.nextDouble()*4,(r.nextDouble()-.5)*3).scale((1-t)*(1-t));Vec3 at=target.add(offset);double a=Math.sin(Math.PI*t)*fade;ribbon(b,m,at,at.add(offset.normalize().scale(.4)),.055,1,.91,.58,a);ringAt(b,m,target,.45*(1-t)+.1,.055,.74,.6,1,a*.6);}
                // Local core rises gently; low-flash mode further reduces the glow.
                double core=(AtomicClient.lowFlash?.35:.65)*fade*AtomicMath.smooth(.1,.8,age);
                for(int i=0;i<5;i++)ring(b,m,.5+i*.13,.15+i*.28,.05,1,.87,.55,core*(1-i*.15));
                BufferUploader.drawWithShader(b.end());drawnFrames++;
            }finally{pose.popPose();}
        }}finally{RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();}
    }
    private static void v(BufferBuilder b,Matrix4f m,Vec3 p,double r,double g,double blue,double a){b.vertex(m,(float)p.x,(float)p.y,(float)p.z).color((float)r,(float)g,(float)blue,(float)Math.max(0,Math.min(1,a))).endVertex();}
    private static void ring(BufferBuilder b,Matrix4f m,double radius,double y,double w,double r,double g,double blue,double a){ringAt(b,m,new Vec3(0,y,0),radius,w,r,g,blue,a);}
    private static void ringAt(BufferBuilder b,Matrix4f m,Vec3 p,double radius,double w,double r,double g,double blue,double alpha){int count=AtomicClientConfig.QUALITY.get().ringSegments;for(int i=0;i<count;i++){double a=i*Math.PI*2/count,c=(i+1)*Math.PI*2/count;v(b,m,p.add(Math.sin(a)*radius,0,Math.cos(a)*radius),r,g,blue,alpha);v(b,m,p.add(Math.sin(c)*radius,0,Math.cos(c)*radius),r,g,blue,alpha);v(b,m,p.add(Math.sin(c)*(radius+w),0,Math.cos(c)*(radius+w)),r,g,blue,0);v(b,m,p.add(Math.sin(a)*(radius+w),0,Math.cos(a)*(radius+w)),r,g,blue,0);}}
    private static void ribbon(BufferBuilder b,Matrix4f m,Vec3 p,Vec3 q,double w,double r,double g,double blue,double a){v(b,m,p.add(-w,0,0),r,g,blue,a);v(b,m,q.add(-w,0,0),r,g,blue,0);v(b,m,q.add(w,0,0),r,g,blue,0);v(b,m,p.add(w,0,0),r,g,blue,a);v(b,m,p.add(0,0,-w),r,g,blue,a);v(b,m,q.add(0,0,-w),r,g,blue,0);v(b,m,q.add(0,0,w),r,g,blue,0);v(b,m,p.add(0,0,w),r,g,blue,a);}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){
        var s=RecoveryClient.own();var mc=Minecraft.getInstance();if(s==null||mc.options.hideGui||mc.screen!=null)return;var g=e.getGuiGraphics();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight(),width=Math.min(330,w-20),left=(w-width)/2,top=h-116;
        var p=s.packet;String state=p.done()?(p.success()?"recovery.shadow_atomic.complete":"recovery.shadow_atomic.failed"):p.committed()?"recovery.shadow_atomic.weaving":"recovery.shadow_atomic.prelude";
        AtomicCaptions.draw(g,s.audio.caption(),w/2,top-18,w,0xfff1e4b6);
        g.fill(left,top,left+width,top+48,0xcc081b1a);g.fill(left,top,left+2,top+48,0xffe8d693);
        g.drawCenteredString(mc.font,Component.translatable(state),w/2,top+5,0xfff1e4b6);
        g.drawCenteredString(mc.font,Component.translatable("recovery.shadow_atomic.progress",p.restored(),p.revived(),p.conflicts()),w/2,top+17,0xffa4dacd);
        g.drawCenteredString(mc.font,Component.translatable(p.committed()?"recovery.shadow_atomic.detach":"recovery.shadow_atomic.cancel",AtomicClient.RECOVERY.getTranslatedKeyMessage()),w/2,top+29,0xff97b9ae);
        double progress=p.done()?1:p.committed()?Math.min(.98,p.total()==0?.1:p.processed()/(double)p.total()):Math.min(1,s.age()/RecoveryAtomic.PRELUDE)*.08;
        g.fill(left+7,top+43,left+width-7,top+45,0xff26483d);g.fill(left+7,top+43,left+7+(int)((width-14)*progress),top+45,0xffb8d6b0);
    }
}
