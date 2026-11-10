package local.shadowatomic.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import local.shadowatomic.*;
import org.lwjgl.glfw.GLFW;
import java.util.*;

@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class AtomicClient {
    public static final KeyMapping SKILL=new KeyMapping("key.shadow_atomic.skill",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,"key.categories.shadow_atomic");
    public static final KeyMapping PLANETARY=new KeyMapping("key.shadow_atomic.planetary",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_B,"key.categories.shadow_atomic");
    public static final KeyMapping RECOVERY=new KeyMapping("key.shadow_atomic.recovery",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_R,"key.categories.shadow_atomic");
    public static final KeyMapping CAMERA=new KeyMapping("key.shadow_atomic.camera",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_J,"key.categories.shadow_atomic");
    public static final KeyMapping FLASH=new KeyMapping("key.shadow_atomic.flash",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_O,"key.categories.shadow_atomic");
    public static final KeyMapping REPLAY=new KeyMapping("key.shadow_atomic.replay",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_P,"key.categories.shadow_atomic");
    public static final KeyMapping QUALITY=new KeyMapping("key.shadow_atomic.quality",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_K,"key.categories.shadow_atomic");
    public static final List<Show> SHOWS=new ArrayList<>();
    public static boolean cinema=true, lowFlash=false;
    private static ArmorStand anchor;
    private static Entity previousCamera;
    private static net.minecraft.client.CameraType previousType;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    public static final class Show {
        public AtomicNetwork.Begin packet;
        public double previousAge;
        public float releaseYaw;
        public boolean blasted,tailed;
        public boolean terrainApplied,terrainReady,terrainFailed,terrainTimedOut,coverReturned;
        private double appliedElapsed,readyElapsed,warmPoseAt=-1;
        private double stableTerrainSince=-1,lastTerrainWaitLog=-1;
        private long[] terrainChunks=new long[0];
        private AtomicTerrainView terrainView;
        public boolean visibleTerrainReady;
        public int visibleTerrainSections,pendingTerrainSections;
        public final AtomicAudio.Session audio;
        private long clockAt;
        private double elapsedAt;
        private Vec3 predictedImpact;
        public Vec3 origin(float partial){
            if(packet.impactLocked())return packet.origin();
            var level=Minecraft.getInstance().level;var caster=level==null?null:level.getEntity(packet.caster());
            Vec3 current=caster==null?packet.origin():caster.getPosition(partial);
            if(packet.releasing()&&elapsed(partial)>=5){if(predictedImpact==null)predictedImpact=current;return predictedImpact;}
            return current;
        }
        private void timeline(AtomicNetwork.Begin p){
            clockAt=AtomicClock.now();var level=Minecraft.getInstance().level;
            elapsedAt=p.elapsedMillis()>=0?p.elapsedMillis()/1000.0:level==null?999:Math.max(0,(level.getGameTime()-p.startTick())/20.0);
        }
        public Show(AtomicNetwork.Begin p){packet=p;timeline(p);terrainReady=p.mode()!=AtomicMode.DESTRUCTION;previousAge=-1;releaseYaw=Minecraft.getInstance().player==null?0:Minecraft.getInstance().player.getYRot();audio=new AtomicAudio.Session(elapsed(0),p.releasing(),p.origin(),local(),p.scale());}
        public double elapsed(float partial){return Math.max(0,elapsedAt+(AtomicClock.now()-clockAt)/1e9);}
        // Holding uses a bounded visual clock. No voice, impact or gameplay timer advances.
        public double age(float partial){double e=elapsed(partial);return packet.releasing()?(needsTerrain()?terrainReady?2+e-Math.max(0,readyElapsed-7.8):Math.min(2+e,9.8):2+e):Math.min(e,4.2)+.12*Math.sin(e*.7)*AtomicMath.smooth(3.5,4.2,e);}
        public boolean needsTerrain(){return packet.releasing()&&packet.mode()==AtomicMode.DESTRUCTION;}
        public float coverOpacity(){
            if(!local()||!cinema||!needsTerrain()||!terrainApplied||terrainFailed||terrainTimedOut||coverReturned)return 0;
            double e=elapsed(0);return (float)(terrainReady?1-AtomicMath.smooth(0,.65,e-readyElapsed):AtomicMath.smooth(0,.15,e-Math.max(appliedElapsed,9.1)));
        }
        private void terrain(AtomicNetwork.Terrain value){
            if(!needsTerrain()||terrainApplied)return;terrainApplied=true;terrainFailed=!value.success();appliedElapsed=elapsed(0);terrainChunks=value.sections();
            if(!terrainFailed)terrainView=new AtomicTerrainView(terrainChunks);
            if(terrainFailed){terrainReady=true;readyElapsed=elapsed(0);AtomicMod.LOG.warn("ATOMIC_TERRAIN_VIEW_FAILED event={}",packet.event());}
        }
        private void updateTerrain(boolean afterRender){
            if(!needsTerrain()||terrainReady)return;var mc=Minecraft.getInstance();double e=elapsed(0);
            if(e>25){terrainTimedOut=true;terrainReady=true;readyElapsed=e;AtomicMod.LOG.warn("ATOMIC_TERRAIN_VIEW_TIMEOUT event={}",packet.event());return;}
            if(!terrainApplied||mc.level==null)return;
            if(local()&&cinema&&!AtomicCover.available()){terrainTimedOut=true;terrainReady=true;readyElapsed=e;AtomicMod.LOG.warn("ATOMIC_COVER_UNAVAILABLE event={}",packet.event());return;}
            if(!afterRender)return;
            boolean poseReady=!local()||!cinema||warmPoseAt>=0&&e-warmPoseAt>.25;
            visibleTerrainReady=poseReady&&terrainView!=null&&terrainView.ready();
            if(terrainView!=null){visibleTerrainSections=terrainView.visible;pendingTerrainSections=terrainView.pending;}
            if(poseReady&&e-appliedElapsed>.25&&visibleTerrainReady){if(stableTerrainSince<0)stableTerrainSince=e;}else stableTerrainSince=-1;
            if(local()&&e-lastTerrainWaitLog>1){lastTerrainWaitLog=e;AtomicMod.LOG.info("ATOMIC_TERRAIN_VIEW_WAIT event={} visible={} pending={} lightQueueEmpty={} afterRender=true",packet.event(),visibleTerrainSections,pendingTerrainSections,mc.level.isLightUpdateQueueEmpty());}
            if(stableTerrainSince>=0&&e-stableTerrainSince>=.2&&mc.level.isLightUpdateQueueEmpty()){
                terrainReady=true;readyElapsed=e;
                AtomicMod.LOG.info("ATOMIC_TERRAIN_VIEW_READY event={} chunks={} visibleSections={} pendingSections={} elapsed={} local={}",packet.event(),terrainChunks.length,visibleTerrainSections,pendingTerrainSections,e,local());
                if(local())AtomicNetwork.terrainAck(packet.event());
            }
        }
        public boolean local(){return Minecraft.getInstance().player!=null && packet.caster()==Minecraft.getInstance().player.getId();}
    }
    public static void begin(AtomicNetwork.Begin packet) {
        var mc=Minecraft.getInstance();
        if(mc.level==null)return;
        Show existing=SHOWS.stream().filter(s->s.packet.event().equals(packet.event())).findFirst().orElse(null);
        if(existing!=null){
            if(packet.releasing()&&!existing.packet.releasing()){existing.packet=packet;existing.timeline(packet);existing.releaseYaw=mc.player==null?0:mc.player.getYRot();existing.audio.release(existing.elapsed(0));}
            else if(packet.releasing()||!existing.packet.releasing())existing.packet=packet;
            return;
        }
        // A per-client cap only limits distant visual work; it never changes server gameplay.
        if(SHOWS.size()>=4)SHOWS.remove(0).audio.stop();
        SHOWS.add(new Show(packet));dimension=mc.level.dimension();
    }
    public static void end(UUID event){
        boolean local=SHOWS.stream().anyMatch(s->s.packet.event().equals(event)&&s.local());
        SHOWS.removeIf(s->{if(!s.packet.event().equals(event))return false;s.audio.stop();return true;});
        if(local)restore();
    }
    public static void terrain(AtomicNetwork.Terrain packet){SHOWS.stream().filter(s->s.packet.event().equals(packet.event())).findFirst().ifPresent(s->s.terrain(packet));}
    public static void cleanup(){restore();SHOWS.forEach(s->s.audio.stop());SHOWS.clear();dimension=null;AtomicInput.clear();AtomicHud.clear();ShadowPowersClient.clear();}
    public static boolean cameraActive(){return anchor!=null;}
    private static void restore() {
        var mc=Minecraft.getInstance();
        if(anchor!=null){mc.setCameraEntity(previousCamera!=null&&!previousCamera.isRemoved()?previousCamera:mc.player);if(previousType!=null)mc.options.setCameraType(previousType);}
        anchor=null; previousCamera=null;previousType=null;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){cleanup();AtomicClock.paused(false);}
    @SubscribeEvent public static void clock(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.START)AtomicClock.paused(Minecraft.getInstance().isPaused());}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||dimension!=null&&!dimension.equals(mc.level.dimension())||mc.player.isDeadOrDying()){cleanup();return;}
        AtomicInput.tick();
        ShadowPowersClient.input();
        SwordInspectionClient.input();
        while(AtomicInput.consume(CAMERA)){cinema=!cinema;if(!cinema)restore();mc.player.displayClientMessage(Component.translatable("message.shadow_atomic.camera",Component.translatable(cinema?"options.on":"options.off")),true);}
        while(AtomicInput.consume(FLASH)){lowFlash=!lowFlash;mc.player.displayClientMessage(Component.translatable("message.shadow_atomic.low_flash",Component.translatable(lowFlash?"options.on":"options.off")),true);}
        while(AtomicInput.consume(QUALITY)){
            var values=AtomicClientConfig.Quality.values();var next=values[(AtomicClientConfig.QUALITY.get().ordinal()+1)%values.length];
            AtomicClientConfig.QUALITY.set(next);AtomicClientConfig.SPEC.save();
            mc.player.displayClientMessage(Component.translatable("message.shadow_atomic.quality",Component.translatable(next.translationKey())),false);
        }
        while(AtomicInput.consume(SKILL))AtomicNetwork.request(false);
        while(AtomicInput.consume(REPLAY))AtomicNetwork.request(true);
        while(AtomicInput.consume(PLANETARY))AtomicNetwork.requestPlanet(false);
        while(AtomicInput.consume(RECOVERY))RecoveryClient.input();
        if(mc.isPaused())return;
        Iterator<Show> it=SHOWS.iterator();
        Show main=null;
        while(it.hasNext()){
            Show s=it.next();s.updateTerrain(false);double age=s.age(0);
            if(s.packet.releasing()&&age>15){s.audio.stop();it.remove();continue;}
            if(s.local()&&s.packet.releasing())main=s;
            s.audio.move(s.origin(1));s.audio.tick(s.elapsed(0),s.packet.releasing(),age);
            if(s.packet.releasing()&&age>=7&&!s.blasted){s.blasted=true;AtomicMod.LOG.info("ATOMIC_VISUAL_BLAST event={} age={}",s.packet.event(),age);}
            s.previousAge=age;
        }
        
        if(main==null||!cinema||main.coverReturned||main.terrainFailed||main.terrainTimedOut||main.age(0)>12){restore();return;}
        if(anchor==null){anchor=new ArmorStand(mc.level,0,0,0);previousCamera=mc.getCameraEntity();previousType=mc.options.getCameraType();mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.setCameraEntity(anchor);}
        double age=main.age(0);
        if(main.needsTerrain()&&main.terrainApplied){
            if(main.warmPoseAt>=0||main.coverOpacity()>=.999f){
                if(main.warmPoseAt<0){main.warmPoseAt=main.elapsed(0);AtomicMod.LOG.info("ATOMIC_CAMERA_WARM_RETURN event={}",main.packet.event());}
                mc.setCameraEntity(previousCamera!=null&&!previousCamera.isRemoved()?previousCamera:mc.player);if(previousType!=null)mc.options.setCameraType(previousType);
                if(main.terrainReady&&main.coverOpacity()<=.001f){main.coverReturned=true;AtomicMod.LOG.info("ATOMIC_CAMERA_TERRAIN_RETURN event={} elapsed={}",main.packet.event(),main.elapsed(0));restore();}
                return;
            }
        }
        Vec3 o=main.origin(1);
        // An observer orbit provides scale without moving the actual player or loading chunks.
        double pull=AtomicMath.smooth(6.7,8.6,age);
        double safeDistance=Math.max(128,mc.options.getEffectiveRenderDistance()*16*.90);
        double theta=Math.toRadians(main.releaseYaw)+.28;
        Vec3 facing=new Vec3(-Math.sin(theta),0,Math.cos(theta));
        double close=3.6-.6*AtomicMath.smooth(2,5.8,age);
        Vec3 near=mc.player.position().add(facing.scale(close)).add(0,1.65,0);
        Vec3 far=o.add(facing.scale(Math.min(380*main.packet.scale(),safeDistance))).add(0,5+Math.min(165,safeDistance*.37),0);
        double u=age-7;
        double displacement=lowFlash?0:(u>=0?2.0*Math.exp(-u*2.1):0);
        Vec3 pos=near.lerp(far,pull).add(Math.sin(age*37)*displacement,Math.sin(age*53)*displacement*.35,0);
        Vec3 target=mc.player.position().add(0,1.35,0).lerp(o.add(0,1.4+Math.min(82,safeDistance*.18),0),pull);
        if(main.needsTerrain()&&!main.terrainReady){
            double cover=AtomicMath.smooth(7.12,7.65,age);
            pos=pos.lerp(o.add(facing.scale(186*main.packet.scale())).add(0,30*main.packet.scale(),0),cover);
            target=target.lerp(o.add(0,340*main.packet.scale(),0),cover);
        }
        if(pull<.08){
            var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(target,pos,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
            if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)pos=hit.getLocation().add(target.subtract(pos).normalize().scale(.15));
        }
        Vec3 delta=target.subtract(pos);
        float yaw=(float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90);
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        anchor.xo=anchor.getX();anchor.yo=anchor.getY();anchor.zo=anchor.getZ();
        anchor.yRotO=anchor.getYRot();anchor.xRotO=anchor.getXRot();
        anchor.yHeadRotO=anchor.getYHeadRot();
        anchor.setPos(pos.add(0,-anchor.getEyeHeight(),0));anchor.setYRot(yaw);anchor.setXRot(pitch);
        // LivingEntity view rotation uses the head, not its body yaw.
        anchor.setYHeadRot(yaw);anchor.setYBodyRot(yaw);
    }
    @SubscribeEvent public static void terrainAfterRender(TickEvent.RenderTickEvent event){
        // Vanilla drains queued light updates and uploads meshes during rendering.
        // A pre-render tick sample can observe a nonempty queue on every tick even
        // though every completed frame has finished processing those same packets.
        if(event.phase==TickEvent.Phase.END)for(var show:SHOWS)show.updateTerrain(true);
    }
    @SubscribeEvent public static void shake(ViewportEvent.ComputeCameraAngles event){
        if(lowFlash)return;
        Show s=SHOWS.stream().filter(show->show.local()&&show.packet.releasing()).findFirst().orElse(null);if(s==null)return;
        double t=s.age((float)event.getPartialTick())-7;
        if(t< -3||t>5)return;
        float strength=(float)(t<0?AtomicMath.smooth(-3,0,t)*.16:Math.exp(-t*2.5)*4.2+Math.exp(-t*.7)*.35);
        event.setYaw(event.getYaw()+(float)(Math.sin(t*57)+.3*Math.sin(t*103))*strength);
        event.setPitch(event.getPitch()+(float)Math.sin(t*71)*strength*.65f);
        event.setRoll(event.getRoll()+(float)Math.cos(t*43)*strength*.38f);
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event){
        Show s=SHOWS.stream().filter(show->show.local()&&show.packet.releasing()).findFirst().orElse(null);if(s==null)return;
        double age=s.age((float)event.getPartialTick());
        if(cameraActive())event.setFOV(event.getFOV()+(105-event.getFOV())*AtomicMath.smooth(6.7,8.6,age));
        if(lowFlash)return;
        double u=age-7;
        if(u>=0&&u<3)event.setFOV(event.getFOV()+9*Math.exp(-u*2.7));
    }
}
