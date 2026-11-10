package local.shadowatomic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import local.shadowatomic.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Expanding Mach-cone rings at actual supersonic motion; bounded, depth-tested meshes. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class ShadowMachFx {
    public static final double SOUND_SPEED=343.0/20;
    private record Ring(Vec3 center,Vec3 normal,long at,boolean mach){}
    private static final List<Ring> RINGS=new ArrayList<>();private static final Map<UUID,Vec3> previous=new HashMap<>();
    private static final Map<UUID,Long> last=new HashMap<>();private static ResourceLocation dimension;private static long boom;
    private record Body(Vec3 normal,double speed,long at){}
    private static final Map<UUID,Body> BODIES=new HashMap<>();
    private static final Set<UUID> SONIC=new HashSet<>();
    public static long emitted,machEmitted,rendered,sonicFrames,lowFlashSonicFrames;
    public static void motion(AtomicNetwork.Motion p){Vec3 d=p.to().subtract(p.from());if(d.lengthSqr()<.01)return;add(p.from().add(0,1,0),d.normalize(),p.mach());}
    private static void add(Vec3 center,Vec3 normal,boolean mach){RINGS.add(new Ring(center,normal,AtomicClock.now(),mach));emitted++;if(mach)machEmitted++;if(RINGS.size()>12)RINGS.remove(0);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null){clear();return;}
        if(!mc.level.dimension().location().equals(dimension)){clear();dimension=mc.level.dimension().location();}
        if(mc.isPaused())return;long now=AtomicClock.now();
        for(var p:mc.level.players()){
            Vec3 old=previous.put(p.getUUID(),p.position());if(old==null||!ShadowGuard.holding(p))continue;
            Vec3 d=p.position().subtract(old);double speed=d.length();
            boolean flying=ShadowFlight.flying(p);
            if(flying&&speed>1&&speed<80)BODIES.put(p.getUUID(),new Body(d.normalize(),speed,now));else BODIES.remove(p.getUUID());
            if(!flying||speed<SOUND_SPEED*.85)SONIC.remove(p.getUUID());
            if(flying&&speed>=SOUND_SPEED&&speed<80&&now-last.getOrDefault(p.getUUID(),0L)>500_000_000L){
                add(p.position().add(0,1,0),d.normalize(),true);last.put(p.getUUID(),now);
                if(SONIC.add(p.getUUID())&&p==mc.player&&now-boom>1_500_000_000L){boom=now;mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(AtomicMod.SONIC_BOOM.get(),.65f,1));}
            }
        }
        Set<UUID> present=new HashSet<>();for(var p:mc.level.players())present.add(p.getUUID());previous.keySet().retainAll(present);last.keySet().retainAll(present);SONIC.retainAll(present);BODIES.keySet().retainAll(present);
    }
    public static void clear(){RINGS.clear();BODIES.clear();previous.clear();last.clear();SONIC.clear();dimension=null;boom=0;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;long now=AtomicClock.now();RINGS.removeIf(r->now-r.at>500_000_000L);BODIES.entrySet().removeIf(x->now-x.getValue().at>250_000_000L);if(RINGS.isEmpty()&&BODIES.isEmpty())return;
        Vec3 camera=e.getCamera().getPosition();Matrix4f matrix=e.getPoseStack().last().pose();var b=Tesselator.getInstance().getBuilder();
        RenderSystem.enableBlend();RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorShader);
        b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        try{
            var mc=Minecraft.getInstance();
            if(mc.level!=null)for(var entry:BODIES.entrySet()){
                var p=mc.level.getPlayerByUUID(entry.getKey());if(p==null)continue;Vec3 n=entry.getValue().normal;
                Vec3 center=p.getPosition(e.getPartialTick()).add(0,p.getBbHeight()*.55,0).subtract(camera);
                Vec3 u=n.cross(Math.abs(n.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),v=n.cross(u).normalize();
                double speed=entry.getValue().speed,flow=AtomicMath.smooth(1,7,speed),sonic=AtomicMath.smooth(SOUND_SPEED*.94,SOUND_SPEED*1.08,speed);
                double phase=(now%350_000_000L)/350_000_000.0;
                double gain=AtomicClient.lowFlash?.35:1;
                // Longitudinal slipstream replaces concentric rings stuck to an upright player.
                for(int j=0;j<10;j++){
                    double a=j*Math.PI*2/10+Math.sin(now/1e9*2+j)*.08;
                    Vec3 radial=u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                    double start=.4+((phase+j*.137)%1)*1.3,length=1.2+flow*3.6;
                    Vec3 p1=center.subtract(n.scale(start)).add(radial.scale(.62+j%3*.12));
                    Vec3 p2=p1.subtract(n.scale(length)).add(radial.scale(.08));
                    stroke(b,matrix,p1,p2,radial,.012+.01*flow,.13*flow*gain,.85,.81,1);
                }
                if(sonic>0){
                    Vec3 front=center.add(n.scale(.55)),back=center.subtract(n.scale(1.9));
                    cone(b,matrix,front,back,u,v,.40,1.05,.045*sonic*gain);
                    band(b,matrix,back,u,v,1.05,1.085,.30*sonic*gain,.91,.88,1);
                    band(b,matrix,front,u,v,.40,.415,.18*sonic*gain,1,1,1);
                    // Keep a continuous ring train near the interpolated body. World-space
                    // shock waves alone disappear behind a fast third-person camera.
                    // Travel direction owns the planes, including strafing and reversing.
                    for(int j=0;j<3;j++){
                        double travel=(phase+j)/3;
                        Vec3 ringCenter=center.subtract(n.scale(-.35+travel*3.1));
                        double radius=.58+travel*.96,alpha=sonic*gain*(.62-travel*.22);
                        band(b,matrix,ringCenter,u,v,radius-.035,radius+.07,alpha*.46,.65,.40,1);
                        band(b,matrix,ringCenter,u,v,radius,radius+.032,alpha,.96,.89,1);
                    }
                    sonicFrames++;if(AtomicClient.lowFlash)lowFlashSonicFrames++;
                }
                rendered++;
            }
            for(Ring r:RINGS){double age=(now-r.at)/1e9,fade=Math.max(0,1-age/.5);Vec3 n=r.normal;Vec3 u=n.cross(Math.abs(n.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),v=n.cross(u).normalize();
                int count=1;
                for(int j=0;j<count;j++){
                    Vec3 center=r.center.subtract(n.scale(.8)).subtract(camera);double radius=(r.mach?.85:.65)+age*(r.mach?5:2);
                    double alpha=fade*fade*(AtomicClient.lowFlash?.18:.48);if(center.length()<radius)alpha*=.15;
                    band(b,matrix,center,u,v,radius,radius+.045,alpha,.92,.87,1);
                }
                rendered++;
            }
            BufferUploader.drawWithShader(b.end());
        }finally{RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();}
    }
    private static void stroke(BufferBuilder b,Matrix4f matrix,Vec3 a,Vec3 z,Vec3 side,double width,double alpha,double r,double g,double blue){
        vertex(b,matrix,a.subtract(side.scale(width)),r,g,blue,0);vertex(b,matrix,z.subtract(side.scale(width*.2)),r,g,blue,alpha);
        vertex(b,matrix,z.add(side.scale(width*.2)),r,g,blue,alpha);vertex(b,matrix,a.add(side.scale(width)),r,g,blue,0);
    }
    private static void cone(BufferBuilder b,Matrix4f matrix,Vec3 front,Vec3 back,Vec3 u,Vec3 v,double small,double large,double alpha){
        for(int i=0;i<40;i++){
            double a=i*Math.PI*2/40,z=(i+1)*Math.PI*2/40;Vec3 p=u.scale(Math.cos(a)).add(v.scale(Math.sin(a))),q=u.scale(Math.cos(z)).add(v.scale(Math.sin(z)));
            vertex(b,matrix,front.add(p.scale(small)),.92,.89,1,0);vertex(b,matrix,back.add(p.scale(large)),.92,.89,1,alpha);
            vertex(b,matrix,back.add(q.scale(large)),.92,.89,1,alpha);vertex(b,matrix,front.add(q.scale(small)),.92,.89,1,0);
        }
    }
    private static void band(BufferBuilder b,Matrix4f matrix,Vec3 c,Vec3 u,Vec3 v,double inner,double outer,double alpha,double red,double green,double blue){
        for(int i=0;i<40;i++){double a=i*Math.PI*2/40,z=(i+1)*Math.PI*2/40;Vec3 p=u.scale(Math.cos(a)).add(v.scale(Math.sin(a))),q=u.scale(Math.cos(z)).add(v.scale(Math.sin(z)));
            vertex(b,matrix,c.add(p.scale(inner)),red,green,blue,alpha);vertex(b,matrix,c.add(q.scale(inner)),red,green,blue,alpha);vertex(b,matrix,c.add(q.scale(outer)),red,green,blue,alpha);vertex(b,matrix,c.add(p.scale(outer)),red,green,blue,alpha);
        }
    }
    private static void vertex(BufferBuilder b,Matrix4f matrix,Vec3 p,double r,double g,double blue,double alpha){b.vertex(matrix,(float)p.x,(float)p.y,(float)p.z).color((float)r,(float)g,(float)blue,(float)alpha).endVertex();}
}
