package local.shadowatomic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import local.shadowatomic.AtomicMod;
import local.shadowatomic.AtomicClientConfig;
import java.util.Random;

@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class AtomicRenderer {
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||AtomicClient.SHOWS.isEmpty())return;
        var mc=Minecraft.getInstance(); if(mc.level==null)return;
        Vec3 cam=event.getCamera().getPosition();
        PoseStack pose=event.getPoseStack();
        RenderSystem.enableBlend();RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
        RenderSystem.disableCull();RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            for(var show:AtomicClient.SHOWS){
                double t=show.age(event.getPartialTick());if(t<0||t>14.5)continue;
                Vec3 o=show.origin(event.getPartialTick());if(o.distanceToSqr(cam)>900*900)continue;
                pose.pushPose();pose.translate(o.x-cam.x,o.y-cam.y+.06,o.z-cam.z);
                pose.scale(show.packet.scale(),show.packet.scale(),show.packet.scale());
                boolean volume=AtomicVolume.forShow(show,cam);
                try{draw(pose.last().pose(),t,show.packet.event().getLeastSignificantBits(),show.packet.releasing(),cam.subtract(o).scale(1.0/show.packet.scale()),show.elapsed(event.getPartialTick()),volume,AtomicVolume.ready()&&!volume,show.packet.scale());}finally{pose.popPose();}
            }
        } finally {
            RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
        }
    }
    private static void draw(Matrix4f m,double t,long seed,boolean releasing,Vec3 camera,double flowClock,boolean volume,boolean budgetFallback,double scale){
        BufferBuilder b=Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        double grow=releasing?1:AtomicMath.smooth(.25,3.4,t),fade=AtomicMath.fade(t);
        // The blast consumes the held-domain drawing. Keeping its complete diagram
        // beneath a solid column made the foot look like a permanently pasted disc.
        double domainFade=fade*(1-AtomicMath.smooth(7,8.1,t));
        AtomicVeins.draw(b,m,seed,grow,domainFade*.70,flowClock,camera,scale);
        if(t<7){
            double charge=AtomicMath.smooth(2.5,6.8,t);
            AtomicVeins.charge(b,m,t,.35+.65*charge,camera,scale,flowClock);
        }else{
            double u=t-7,r=AtomicMath.radius(t),f=fade;
            if(!volume&&!budgetFallback){
            // Compatibility fallback if volume shading is unavailable.
            sphere(b,m,0,5,0,r, .44,.015,1,.075*f,64,32,t);
            sphere(b,m,0,5,0,r*.94,.78,.13,1,.14*f,64,32,t+2);
            sphere(b,m,0,5,0,r*.78,.88,.48,1,.22*f,48,24,t+5);
            sphere(b,m,0,5,0,r*.54,1,.82,1,.55*f,40,20,t);
            sphere(b,m,0,5,0,r*.32,1,1,1,.95*f,32,16,t);
            }
            // Three finite travelling fronts, rather than nine waves parked at their
            // final radius for the entire tail. They leave the dense pillar readable.
            for(int i=0;i<3;i++){
                double progress=AtomicMath.clamp((u-i*.18)/2.2);
                double rr=512*(1-(1-progress)*(1-progress));
                double wave=AtomicMath.smooth(0,.06,progress)*(1-AtomicMath.smooth(.64,1,progress))*f;
                if(rr>1&&wave>.001){ring(b,m,rr,.3+i*.12,2.0+rr*.009,.35,.015,1,.42*wave);ring(b,m,rr*.995,.45+i*.12,.45,.7,.5,1,.64*wave);}
            }
            // The anime-scale pillar is the dominant silhouette, not a thin laser.
            // Broad concentric light volumes create a white core and a thick violet corona.
            double pillar=AtomicMath.smooth(7.0,7.45,t);
            double width=pillar*(.95+.05*Math.sin(u*5));
            if(!volume){
            if(budgetFallback){
            // Distant over-budget events keep a pillar/core silhouette. Repeating five
            // bright sphere shells here would wash out the primary event in a crowd.
            column(b,m,76*width,-4,980,.48,.025,1,.10*f);
            column(b,m,2*width,-4,980,1,.85,1,.60*f);
            }else{
            column(b,m,76*width,-4,680,.48,.025,1,.12*f);
            column(b,m,64*width,-4,760,.72,.09,1,.22*f);
            column(b,m,49*width,-4,840,.92,.38,1,.38*f);
            column(b,m,34*width,-4,920,1,.80,1,.65*f);
            column(b,m,21*width,-4,980,1,1,1,.95*f);
            }
            }
            Random rand=new Random(seed+17);
            for(int i=0;i<AtomicClientConfig.QUALITY.get().sparks;i++){
                double a=rand.nextDouble()*Math.PI*2;
                double h=rand.nextDouble()*2-1;
                double rr=r*(.98+rand.nextDouble()*.13);
                double x=Math.sin(a)*Math.sqrt(1-h*h)*rr,z=Math.cos(a)*Math.sqrt(1-h*h)*rr,y=5+Math.abs(h)*rr;
                beam(b,m,x,y,z,x*1.13,y*1.08,z*1.13,.18,.85,.4,1,.8*f);
            }
        }
        BufferUploader.drawWithShader(b.end());
    }
    private static void v(BufferBuilder b,Matrix4f m,double x,double y,double z,double r,double g,double blue,double a){b.vertex(m,(float)x,(float)y,(float)z).color((float)r,(float)g,(float)blue,(float)Math.max(0,Math.min(1,a))).endVertex();}
    private static void ring(BufferBuilder b,Matrix4f m,double radius,double y,double width,double r,double g,double blue,double alpha){
        int segments=AtomicClientConfig.QUALITY.get().ringSegments;
        for(int i=0;i<segments;i++){
            double a=i*Math.PI*2/segments,c=(i+1)*Math.PI*2/segments;
            v(b,m,Math.sin(a)*radius,y,Math.cos(a)*radius,r,g,blue,alpha);
            v(b,m,Math.sin(c)*radius,y,Math.cos(c)*radius,r,g,blue,alpha);
            v(b,m,Math.sin(c)*(radius+width),y,Math.cos(c)*(radius+width),r,g,blue,0);
            v(b,m,Math.sin(a)*(radius+width),y,Math.cos(a)*(radius+width),r,g,blue,0);
        }
    }
    private static void beam(BufferBuilder b,Matrix4f m,double x,double y,double z,double x2,double y2,double z2,double width,double r,double g,double blue,double alpha){
        double dx=x2-x,dz=z2-z,len=Math.sqrt(dx*dx+dz*dz);double nx=len<.001?width:-dz/len*width,nz=len<.001?0:dx/len*width;
        v(b,m,x-nx,y,z-nz,r,g,blue,alpha);v(b,m,x2-nx,y2,z2-nz,r,g,blue,alpha);v(b,m,x2+nx,y2,z2+nz,r,g,blue,alpha);v(b,m,x+nx,y,z+nz,r,g,blue,alpha);
        v(b,m,x,y-width,z,r,g,blue,alpha*.5);v(b,m,x2,y2-width,z2,r,g,blue,alpha*.5);v(b,m,x2,y2+width,z2,r,g,blue,alpha*.5);v(b,m,x,y+width,z,r,g,blue,alpha*.5);
    }
    private static void column(BufferBuilder b,Matrix4f m,double radius,double bottom,double top,double r,double g,double blue,double alpha){
        int segments=AtomicClientConfig.QUALITY.get().columnSegments;
        for(int i=0;i<segments;i++){
            double a=i*Math.PI*2/segments,c=(i+1)*Math.PI*2/segments;
            v(b,m,Math.sin(a)*radius,bottom,Math.cos(a)*radius,r,g,blue,alpha);
            v(b,m,Math.sin(c)*radius,bottom,Math.cos(c)*radius,r,g,blue,alpha);
            v(b,m,Math.sin(c)*radius*.88,top,Math.cos(c)*radius*.88,r,g,blue,0);
            v(b,m,Math.sin(a)*radius*.88,top,Math.cos(a)*radius*.88,r,g,blue,0);
        }
    }
    private static void sphere(BufferBuilder b,Matrix4f m,double cx,double cy,double cz,double radius,double r,double g,double blue,double alpha,int slices,int stacks,double time){
        double quality=AtomicClientConfig.QUALITY.get().meshScale;slices=Math.max(12,(int)Math.round(slices*quality));stacks=Math.max(6,(int)Math.round(stacks*quality));
        for(int j=0;j<stacks;j++)for(int i=0;i<slices;i++){
            double theta=i*Math.PI*2/slices,theta2=(i+1)*Math.PI*2/slices,phi=-Math.PI/2+j*Math.PI/stacks,phi2=-Math.PI/2+(j+1)*Math.PI/stacks;
            double pulse=.55+.45*Math.pow(.5+.5*Math.sin(theta*11+phi*18-time*9+Math.sin(theta*7-phi*9)*2),5);
            point(b,m,cx,cy,cz,radius,theta,phi,r,g,blue,alpha*pulse);point(b,m,cx,cy,cz,radius,theta2,phi,r,g,blue,alpha*pulse);point(b,m,cx,cy,cz,radius,theta2,phi2,r,g,blue,alpha*pulse);point(b,m,cx,cy,cz,radius,theta,phi2,r,g,blue,alpha*pulse);
        }
    }
    private static void point(BufferBuilder b,Matrix4f m,double cx,double cy,double cz,double radius,double theta,double phi,double r,double g,double blue,double alpha){v(b,m,cx+radius*Math.cos(phi)*Math.sin(theta),cy+radius*Math.sin(phi),cz+radius*Math.cos(phi)*Math.cos(theta),r,g,blue,alpha);}
    @SubscribeEvent public static void hand(RenderHandEvent event){if(AtomicClient.cameraActive())event.setCanceled(true);}
    @SubscribeEvent public static void name(net.minecraftforge.client.event.RenderNameTagEvent event){if(AtomicClient.cameraActive()&&event.getEntity()==Minecraft.getInstance().player)event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);}
    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Pre event){
        if(AtomicClient.cameraActive()&&(event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())||event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())))event.setCanceled(true);
    }
    @SubscribeEvent public static void overlay(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();var s=AtomicClient.SHOWS.stream().filter(AtomicClient.Show::local).findFirst().orElse(null);if(s==null)return;
        double t=s.age(event.getPartialTick());var g=event.getGuiGraphics();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        double dark=AtomicVolume.ready()?0:(AtomicMath.smooth(0,2,t))*(1-AtomicMath.smooth(6.8,7.05,t));
        if(dark>0)g.fill(0,0,w,h,((int)(dark*65)<<24)|0x090015);
        if(!s.packet.releasing()){
            if(!mc.options.hideGui&&mc.screen==null)AtomicCaptions.draw(g,s.audio.caption(),w/2,(int)(h*.83),w,0xffdcadff);
            return;
        }
        if(AtomicClient.cameraActive()){g.fill(0,0,w,Math.max(8,h/16),0xee05020a);g.fill(0,h-Math.max(8,h/16),w,h,0xee05020a);}
        if(t>=6.96&&t<7.18&&!AtomicClient.lowFlash){
            boolean white=t>=7.03;g.fill(0,0,w,h,white?0xeeffffff:0xe9000000);
            // Sharp single impact graphic, not a continuous strobe.
            g.fill(w/2-1,0,w/2+1,h,white?0xff160020:0xffffffff);
            g.fill(0,h/2-1,w,h/2+1,white?0xff160020:0xffffffff);
            AtomicSilhouette.draw(g,white,false);
        }else if(t>=6.96&&t<7.28&&AtomicClient.lowFlash){
            g.fill(0,0,w,h,0x55090015);AtomicSilhouette.draw(g,false,true);
        }
        String title=s.audio.caption();
        if(mc.options.hideGui||mc.screen!=null)return;
        AtomicCaptions.draw(g,title,w/2,(int)(h*.83),w,t>=6.96?0xffffffff:0xffdcadff);
    }
}
