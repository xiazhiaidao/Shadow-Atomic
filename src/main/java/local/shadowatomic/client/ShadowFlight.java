package local.shadowatomic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import local.shadowatomic.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import java.util.*;

/** Creative-style travel with deliberate sprint boost; vanilla owns direction and collision. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class ShadowFlight {
    public enum Stage { NONE,HOVER,ACCELERATE,CRUISE,SONIC,BRAKE }
    public static Stage stage=Stage.NONE;
    public static double speed;public static float throttle;
    private static Vec3 before;
    private static boolean wasFlying,boostArmed;
    private static UUID owner;private static net.minecraft.resources.ResourceLocation dimension;
    private static final Map<UUID,View> VIEWS=new HashMap<>();
    private static boolean manual;
    public static long posedFrames;
    public static final class View {
        public Vec3 previous,direction=new Vec3(0,0,1);
        public float blend,oldBlend,bank,oldBank,speed;
        public float blend(float partial){return Mth.lerp(partial,oldBlend,blend);}
        public float bank(float partial){return Mth.lerp(partial,oldBank,bank);}
    }
    public static View view(Player p){return VIEWS.get(p.getUUID());}
    public static boolean flying(Player p){return p.isAlive()&&ShadowGuard.holding(p)&&p.getAbilities().flying&&!p.isPassenger()&&!p.isSpectator();}
    public static void clear(){stage=Stage.NONE;speed=0;throttle=0;before=null;owner=null;dimension=null;wasFlying=false;boostArmed=false;VIEWS.clear();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        var mc=Minecraft.getInstance();var p=mc.player;
        if(p==null||mc.level==null){clear();return;}
        if(mc.isPaused())return;
        if(e.phase==TickEvent.Phase.END){updateViews();return;}
        if(!p.getUUID().equals(owner)||!mc.level.dimension().location().equals(dimension)){
            clear();owner=p.getUUID();dimension=mc.level.dimension().location();
        }
        if(!ShadowPowersClient.state.active()||!flying(p)){
            if(wasFlying&&throttle>0)p.setDeltaMovement(Vec3.ZERO);
            stage=Stage.NONE;speed=0;throttle=0;before=null;wasFlying=false;boostArmed=false;return;
        }
        boolean sprint=mc.options.keySprint.isDown();
        if(!wasFlying){boostArmed=!sprint;wasFlying=true;}
        if(!sprint)boostArmed=true;
        if(before!=null&&p.position().distanceToSqr(before)>80*80){throttle=0;boostArmed=!sprint;}
        before=p.position();
        int forward=(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0);
        int left=(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0);
        boolean directional=forward!=0||left!=0;
        if(mc.screen!=null||mc.getOverlay()!=null)boostArmed=false;
        boolean boost=boostArmed&&sprint&&directional&&mc.screen==null&&mc.getOverlay()==null;
        if(boost){
            float yaw=p.getYRot()*Mth.DEG_TO_RAD;
            Vec3 desired=new Vec3(left*Mth.cos(yaw)-forward*Mth.sin(yaw),0,forward*Mth.cos(yaw)+left*Mth.sin(yaw)).normalize();
            Vec3 horizontal=new Vec3(p.getDeltaMovement().x,0,p.getDeltaMovement().z);
            if(horizontal.lengthSqr()>1&&horizontal.normalize().dot(desired)<.6)p.setDeltaMovement(p.getDeltaMovement().multiply(.4,1,.4));
        }
        float previous=throttle;
        throttle=Mth.approach(throttle,boost?1:0,boost?.05f:.2f);
        float normal=ShadowPowersClient.state.normal();
        float eased=throttle*throttle*(3-2*throttle);
        // Changing the flight acceleration leaves every directional/vertical input to vanilla.
        // Never zero its input acceleration or force velocity along the look vector.
        p.getAbilities().setFlyingSpeed(boost?Mth.lerp(eased,normal,Math.max(normal,ShadowPowersClient.state.boost())):normal);
        if(!boost&&previous>0)p.setDeltaMovement(p.getDeltaMovement().scale(.45));
        speed=throttle>0?p.getDeltaMovement().length():0;
        stage=boost?(speed>=ShadowMachFx.SOUND_SPEED?Stage.SONIC:throttle<.99?Stage.ACCELERATE:Stage.CRUISE):previous>0?Stage.BRAKE:Stage.HOVER;
        p.fallDistance=0;
    }

    private static void updateViews(){
        var mc=Minecraft.getInstance();Set<UUID> present=new HashSet<>();
        for(var p:mc.level.players()){
            present.add(p.getUUID());View v=VIEWS.computeIfAbsent(p.getUUID(),k->new View());
            Vec3 delta=v.previous==null?Vec3.ZERO:p.position().subtract(v.previous);v.previous=p.position();
            v.oldBlend=v.blend;v.oldBank=v.bank;
            boolean air=flying(p);double distance=delta.length();
            if(distance>80){v.blend=v.oldBlend=0;v.bank=v.oldBank=0;v.speed=0;continue;}
            v.speed=(float)distance;
            Vec3 n=distance>.08?delta.normalize():p.getLookAngle();
            float target=air?(float)AtomicMath.smooth(.4,4.2,distance):0;
            float yawTurn=(float)v.direction.cross(n).y;
            v.bank=Mth.lerp(.22f,v.bank,Mth.clamp(yawTurn*-110,-24,24)*target);
            v.blend=Mth.approach(v.blend,target,.10f);v.direction=n;
        }
        VIEWS.keySet().retainAll(present);
    }
    private static boolean renderBody(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.getCameraEntity()!=mc.player||mc.options.getCameraType().isFirstPerson()||AtomicClient.cameraActive()||PlanetaryClient.macroActive())return false;
        View v=view(mc.player);return v!=null&&(flying(mc.player)||v.blend>.01);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hide(RenderPlayerEvent.Pre e){
        if(!manual&&e.getEntity()==Minecraft.getInstance().player&&renderBody())e.setCanceled(true);
    }
    @SubscribeEvent public static void body(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||!renderBody())return;
        var mc=Minecraft.getInstance();var p=mc.player;View v=view(p);float partial=e.getPartialTick(),blend=v.blend(partial);
        Vec3 at=p.getPosition(partial).subtract(e.getCamera().getPosition()),n=v.direction;
        Vec3 axis=new Vec3(n.z,0,-n.x).normalize();
        float tilt=(float)Math.acos(Mth.clamp(n.y,-1,1))*blend;
        var pose=e.getPoseStack();pose.pushPose();manual=true;
        try{
            pose.translate(at.x,at.y+1,at.z);
            if(axis.lengthSqr()>.01)pose.mulPose(new Quaternionf().rotationAxis(tilt,(float)axis.x,(float)axis.y,(float)axis.z));
            pose.mulPose(new Quaternionf().rotationAxis(v.bank(partial)*Mth.DEG_TO_RAD,(float)n.x,(float)n.y,(float)n.z));
            float bodyYaw=Mth.rotLerp(partial,p.yBodyRotO,p.yBodyRot),heading=(float)(Mth.atan2(-n.x,n.z)*Mth.RAD_TO_DEG);
            pose.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(bodyYaw-heading)*blend));
            pose.translate(0,-1,0);
            var renderer=mc.getEntityRenderDispatcher().getRenderer(p);var buffers=mc.renderBuffers().bufferSource();
            renderer.render(p,p.getYRot(),partial,pose,buffers,renderer.getPackedLightCoords(p,partial));buffers.endBatch();posedFrames++;
        }finally{manual=false;pose.popPose();}
    }
    @SubscribeEvent public static void fov(ComputeFovModifierEvent e){
        View v=view(e.getPlayer());if(v!=null&&flying(e.getPlayer()))e.setNewFovModifier(1+.18f*Math.min(1,v.speed/22));
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.getCameraEntity()!=mc.player||AtomicClient.cameraActive()||PlanetaryClient.macroActive()||mc.screen!=null||AtomicClient.lowFlash)return;
        View v=view(mc.player);if(v==null||!flying(mc.player))return;
        e.setRoll(e.getRoll()+v.bank((float)e.getPartialTick())*.16f);
        float sonic=(float)AtomicMath.smooth(ShadowMachFx.SOUND_SPEED,22,v.speed);
        e.setPitch(e.getPitch()+(float)Math.sin(AtomicClock.now()/1e9*33)*.09f*sonic);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
}
