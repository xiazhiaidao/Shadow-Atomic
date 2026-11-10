package local.shadowatomic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import local.shadowatomic.AtomicMod;
import local.shadowatomic.AtomicNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side readability for a refused hit: impact sparks, a short shield arc hugging the
 * defender and (only for the defender) a very small camera kick. Deliberately silent: no
 * electronic cue is played, so the moment reads as weight rather than a UI notification.
 *
 * <p>Everything here is a reaction to a server message; it never decides damage.
 */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class ShadowGuardFx {
    private static final double SHIELD_SECONDS=.34;
    private static final class Hit {
        final int player;final Vec3 center,normal;final float amount;final long start;
        Hit(int player,Vec3 center,Vec3 normal,float amount,long start){this.player=player;this.center=center;this.normal=normal;this.amount=amount;this.start=start;}
    }
    private static final List<Hit> HITS=new ArrayList<>();
    private static final Map<Integer,Long> LAST_FX=new HashMap<>();
    private static long shakeAt;
    private static float shakeStrength;
    /** QA counters: a probe can assert the server actually told this client to react. */
    public static int received,rendered,shakes;

    public static void hit(AtomicNetwork.Guard message){
        var mc=Minecraft.getInstance();var level=mc.level;
        if(level==null)return;
        received++;
        long now=System.nanoTime();
        var entity=level.getEntity(message.player());
        Vec3 base=entity==null?message.point():entity.position().add(0,entity.getBbHeight()*.62,0);
        Vec3 normal=message.direction().lengthSqr()<1.0E-6?new Vec3(0,0,1):message.direction().normalize();
        boolean local=entity!=null&&entity==mc.player;
        Long last=LAST_FX.get(message.player());
        if(last!=null&&now-last<90_000_000L)return;
        LAST_FX.put(message.player(),now);
        sparks(level,base,normal,message.amount(),local);
        HITS.add(new Hit(message.player(),base,normal,message.amount(),now));
        if(HITS.size()>6)HITS.remove(0);
        if(local){
            shakeAt=now;shakeStrength=AtomicClient.lowFlash?0:.62f;if(shakeStrength>0)shakes++;
        }
    }

    private static void sparks(net.minecraft.client.multiplayer.ClientLevel level,Vec3 center,Vec3 normal,float amount,boolean local){
        double strength=Math.min(1.0,Math.max(.35,amount/8.0));
        Vec3[] basis=basis(normal);
        var rand=level.random;
        int count=local?18:14;
        for(int i=0;i<count;i++){
            double angle=(rand.nextDouble()*2-1)*1.35;
            double radius=.70+rand.nextDouble()*.22;
            Vec3 radial=basis[0].scale(Math.cos(angle)).add(basis[1].scale(Math.sin(angle)));
            Vec3 at=center.add(radial.scale(radius));
            Vec3 tangent=basis[0].scale(-Math.sin(angle)).add(basis[1].scale(Math.cos(angle))).scale((rand.nextDouble()-.5)*.7);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,at.x,at.y,at.z,tangent.x+normal.x*.16,tangent.y+normal.y*.16,tangent.z+normal.z*.16);
            // A few small crit sparks read as impact debris; large end rods looked like smoke puffs.
            if(i%3==0)level.addParticle(ParticleTypes.CRIT,at.x,at.y,at.z,normal.x*.3+radial.x*.1,normal.y*.3+radial.y*.1,normal.z*.3+radial.z*.1);
            if(i%6==0)level.addParticle(ParticleTypes.END_ROD,at.x,at.y,at.z,normal.x*.1,normal.y*.1,normal.z*.1);
        }
        for(int i=0;i<5;i++){
            double angle=rand.nextDouble()*Math.PI*2,radius=.30+rand.nextDouble()*.18;
            Vec3 at=center.add(basis[0].scale(Math.cos(angle)*radius)).add(basis[1].scale(Math.sin(angle)*radius));
            level.addParticle(ParticleTypes.CRIT,at.x,at.y,at.z,normal.x*.24,normal.y*.24,normal.z*.24);
        }
    }

    private static Vec3[] basis(Vec3 normal){
        Vec3 helper=Math.abs(normal.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0);
        Vec3 u=normal.cross(helper).normalize();
        Vec3 v=normal.cross(u).normalize();
        return new Vec3[]{u,v};
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        long now=System.nanoTime();
        HITS.removeIf(hit->now-hit.start>(long)(SHIELD_SECONDS*1e9));
        if(HITS.isEmpty())return;
        // Never begin an empty batch: a fully faded hit can still be inside the list for a frame.
        boolean any=false;
        for(Hit hit:HITS){
            double age=(now-hit.start)/1e9;
            if(AtomicMath.smooth(0,.045,age)*(1-AtomicMath.smooth(.14,SHIELD_SECONDS,age))>.002){any=true;break;}
        }
        if(!any)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        Vec3 cam=event.getCamera().getPosition();
        PoseStack pose=event.getPoseStack();
        double dim=AtomicClient.lowFlash?.55:1;
        RenderSystem.enableBlend();RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
        RenderSystem.disableCull();RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer=Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        try{
            for(Hit hit:HITS){
                double age=(now-hit.start)/1e9;
                double fade=AtomicMath.smooth(0,.045,age)*(1-AtomicMath.smooth(.14,SHIELD_SECONDS,age));
                if(fade<=.002)continue;
                double grow=1+.26*AtomicMath.smooth(0,SHIELD_SECONDS,age);
                Vec3[] basis=basis(hit.normal);
                pose.pushPose();
                pose.translate(hit.center.x-cam.x,hit.center.y-cam.y,hit.center.z-cam.z);
                Matrix4f matrix=pose.last().pose();
                // A domed cap faces the blow instead of a flat disc, so the refusal reads from
                // any camera angle. Body is violet and the rim is the brightest part.
                dome(buffer,matrix,hit.normal,basis,.80*grow,fade*.42,.62,1.10,.58,.28,1,3);
                dome(buffer,matrix,hit.normal,basis,.84*grow,fade*.75,.92,1.10,1,.97,.94,2);
                // Flat echo in the impact plane keeps the strike point crisp.
                arc(buffer,matrix,basis,.66*grow,.90*grow,1.30,fade,.62,.40,1,.26*dim);
                // Short radial spikes break the perfectly circular read.
                for(int i=0;i<5;i++){
                    double angle=-1.05+i*.525+Math.sin((age*17)+i)*.03;
                    Vec3 radial=basis[0].scale(Math.cos(angle)).add(basis[1].scale(Math.sin(angle)));
                    spike(buffer,matrix,radial,.58*grow,1.20*grow,fade*.8,.80,.66,1);
                }
                pose.popPose();
                rendered++;
            }
        }finally{
            RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void arc(BufferBuilder buffer,Matrix4f matrix,Vec3[] basis,double inner,double outer,double halfSpan,double alpha,double r,double g,double b,double strength){
        int segments=18;
        for(int i=0;i<segments;i++){
            double a0=-halfSpan+i*(2*halfSpan/segments),a1=-halfSpan+(i+1)*(2*halfSpan/segments);
            double edge=1-Math.pow(Math.abs((a0+a1)*.5/halfSpan),2);
            double a=alpha*edge*strength;
            Vec3 in0=basis[0].scale(Math.cos(a0)*inner).add(basis[1].scale(Math.sin(a0)*inner));
            Vec3 in1=basis[0].scale(Math.cos(a1)*inner).add(basis[1].scale(Math.sin(a1)*inner));
            Vec3 out1=basis[0].scale(Math.cos(a1)*outer).add(basis[1].scale(Math.sin(a1)*outer));
            Vec3 out0=basis[0].scale(Math.cos(a0)*outer).add(basis[1].scale(Math.sin(a0)*outer));
            vertex(buffer,matrix,in0,r,g,b,0);
            vertex(buffer,matrix,in1,r,g,b,0);
            vertex(buffer,matrix,out1,r,g,b,a);
            vertex(buffer,matrix,out0,r,g,b,a);
        }
    }

    /**
     * A band on a sphere shell facing the blow. Keeping only the rim (instead of a full cap)
     * leaves the character silhouette readable while still reading as a curved shield.
     */
    private static void dome(BufferBuilder buffer,Matrix4f matrix,Vec3 normal,Vec3[] basis,double radius,double alpha,double thetaFrom,double thetaTo,double r,double g,double b,int stacks){
        int slices=22;
        for(int j=0;j<stacks;j++){
            double t0=thetaFrom+(thetaTo-thetaFrom)*j/stacks,t1=thetaFrom+(thetaTo-thetaFrom)*(j+1)/stacks;
            double a0=alpha*(stacks==1?1:j/(double)stacks),a1=alpha*(j+1)/(double)stacks;
            for(int i=0;i<slices;i++){
                double p0=i*Math.PI*2/slices,p1=(i+1)*Math.PI*2/slices;
                Vec3 q00=shell(normal,basis,t0,p0,radius),q01=shell(normal,basis,t0,p1,radius);
                Vec3 q10=shell(normal,basis,t1,p0,radius),q11=shell(normal,basis,t1,p1,radius);
                vertex(buffer,matrix,q00,r,g,b,a0);
                vertex(buffer,matrix,q10,r,g,b,a1);
                vertex(buffer,matrix,q11,r,g,b,a1);
                vertex(buffer,matrix,q01,r,g,b,a0);
            }
        }
    }

    private static Vec3 shell(Vec3 normal,Vec3[] basis,double theta,double phi,double radius){
        double sin=Math.sin(theta);
        return normal.scale(Math.cos(theta)*radius)
            .add(basis[0].scale(Math.cos(phi)*sin*radius))
            .add(basis[1].scale(Math.sin(phi)*sin*radius));
    }

    private static void spike(BufferBuilder buffer,Matrix4f matrix,Vec3 radial,double from,double to,double alpha,double r,double g,double b){
        Vec3 tangent=new Vec3(-radial.z,0,radial.x);
        if(tangent.lengthSqr()<1.0E-6)tangent=new Vec3(1,0,0);
        tangent=tangent.normalize().scale(.018);
        Vec3 a=radial.scale(from),c=radial.scale(to);
        vertex(buffer,matrix,a.subtract(tangent),r,g,b,alpha*.35);
        vertex(buffer,matrix,a.add(tangent),r,g,b,alpha*.35);
        vertex(buffer,matrix,c.add(tangent),r,g,b,alpha*.9);
        vertex(buffer,matrix,c.subtract(tangent),r,g,b,alpha*.9);
    }

    private static void vertex(BufferBuilder buffer,Matrix4f matrix,Vec3 p,double r,double g,double b,double a){
        buffer.vertex(matrix,(float)p.x,(float)p.y,(float)p.z).color((float)r,(float)g,(float)b,(float)Math.max(0,Math.min(1,a))).endVertex();
    }

    @SubscribeEvent public static void shake(ViewportEvent.ComputeCameraAngles event){
        if(shakeStrength<=0)return;
        double age=(System.nanoTime()-shakeAt)/1e9;
        if(age<0||age>.24){shakeStrength=0;return;}
        double decay=1-age/.24;
        float strength=(float)(decay*decay*shakeStrength*.55);
        event.setYaw(event.getYaw()+(float)(Math.sin(age*137)*strength));
        event.setPitch(event.getPitch()+(float)(Math.sin(age*163)*strength*.7));
        event.setRoll(event.getRoll()+(float)(Math.cos(age*121)*strength*.5));
    }

    @SubscribeEvent public static void clear(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        long now=System.nanoTime();
        HITS.removeIf(hit->now-hit.start>(long)(SHIELD_SECONDS*1e9));
        if(LAST_FX.size()>32)LAST_FX.clear();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){
        HITS.clear();LAST_FX.clear();shakeStrength=0;
    }

    public static void reset(){
        HITS.clear();LAST_FX.clear();shakeStrength=0;received=0;rendered=0;
    }
}
