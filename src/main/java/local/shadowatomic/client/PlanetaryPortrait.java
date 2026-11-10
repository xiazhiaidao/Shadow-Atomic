package local.shadowatomic.client;

import local.shadowatomic.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.IOException;
import java.util.*;

/** Perspective actor pass using an unregistered player stand-in, never the live entity. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class PlanetaryPortrait {
    public enum Shot { ORBIT,TOUCH,TARGET,FACE,HAND,RELEASE }
    public static final Set<Shot> drawn=EnumSet.noneOf(Shot.class);
    /** Shot order as actually played, so the enemy-then-hero beat is checked, not assumed. */
    public static final java.util.List<Shot> order=new java.util.ArrayList<>();
    public static boolean playerUntouched=true;
    public static boolean usedStandIn,stateRestored=true,playerSkinPreserved=true;
    public static boolean targetVisible,targetCloseSeen;
    /** Projected vertical coverage of the locked creature during its close-up; 0 outside that shot. */
    public static double targetFill,targetDistance;
    public static boolean targetFramed=true;
    /** Sword-grip framing for the 9.8-11.5 s hand beat; the grip must sit inside the middle of frame. */
    public static double handDistance,handNdcX,handNdcY;
    public static boolean handFramed=true,handProfile=true,handLogged;
    public static boolean duelFacing=true,closeupProfile=true;
    public static UUID visualTarget;
    public static ResourceLocation visualTargetType;
    private static ShaderInstance shader;
    private static RenderTarget target;
    private static RemotePlayer actor;
    private static LivingEntity opponent;
    private static PlayerRenderer renderer;
    private static AbstractClientPlayer source;
    private static UUID eventId;
    private static final Vector3f HERO_POSITION=new Vector3f(2.4f,-.12f,1.6f),ENEMY_POSITION=new Vector3f(-2.4f,.18f,-1.6f);
    private static final Vector3f DUEL_AXIS=new Vector3f(HERO_POSITION).sub(ENEMY_POSITION).mul(1,0,1).normalize();
    private static final Vector3f CAMERA_SIDE=new Vector3f(DUEL_AXIS).cross(0,1,0).normalize();
    private static final float HERO_YAW=(float)Math.toDegrees(-Math.atan2(-DUEL_AXIS.x,-DUEL_AXIS.z)),ENEMY_YAW=HERO_YAW-180;
    private static final MultiBufferSource.BufferSource buffers=MultiBufferSource.immediate(new BufferBuilder(65536));
    private static final HumanoidModel.ArmPose POSE=HumanoidModel.ArmPose.create("SHADOW_PLANETARY",true,PlanetaryPortrait::pose);
    public static boolean ready(){return shader!=null;}
    public static boolean allocated(){return target!=null;}
    public static boolean standIn(){return actor!=null&&(Object)actor!=Minecraft.getInstance().player;}
    public static boolean isActor(LivingEntity entity){return entity==actor;}
    public static PlanetaryClient.Show show(LivingEntity entity){
        if(entity==actor)return PlanetaryClient.own();
        if(entity==opponent)return null;
        return PlanetaryClient.SHOWS.values().stream().filter(s->s.packet.caster()==entity.getId()).findFirst().orElse(null);
    }
    public static HumanoidModel.ArmPose armPose(){return POSE;}
    /**
     * Enemy shot 6.4-8.25, then a hard cut straight to the caster's own close-up. The old
     * 8.05-8.25 ORBIT gap sent the camera back through the wide shot for a fifth of a second,
     * which read as the view swinging right around before the hero shot.
     */
    public static float fov(double t){if(PlanetaryTouch.active(t))return 48;double close=t>=8.25?1-AtomicMath.smooth(11.4,12.6,t):0;return t>=6.4&&t<8.25?45:(float)(58-20*close);}
    public static Shot shot(double t){return PlanetaryTouch.active(t)?Shot.TOUCH:t>=6.4&&t<8.25?Shot.TARGET:t<8.25||t>=16.3?Shot.ORBIT:t<10.0?Shot.FACE:t<11.5?Shot.HAND:Shot.RELEASE;}
    @Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class PortraitShaders {
        @SubscribeEvent public static void registerPortraitShader(RegisterShadersEvent event)throws IOException{
            event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation(AtomicMod.ID,"planetary_actor"),DefaultVertexFormat.POSITION_TEX),s->{shader=s;AtomicMod.LOG.info("PLANETARY_ACTOR_SHADER_READY");});
        }
    }
    @SubscribeEvent public static void name(RenderNameTagEvent event){if(event.getEntity()==actor||event.getEntity()==opponent)event.setResult(Event.Result.DENY);}
    public static void release(){if(target!=null)target.destroyBuffers();target=null;actor=null;source=null;renderer=null;opponent=null;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){release();}
    private static void copyPlayer(PlanetaryClient.Show show){
        var mc=Minecraft.getInstance();
        if(actor==null||source!=mc.player||!show.packet.event().equals(eventId)){
            source=mc.player;eventId=show.packet.event();drawn.clear();order.clear();playerUntouched=true;usedStandIn=false;stateRestored=true;playerSkinPreserved=true;targetVisible=false;targetCloseSeen=false;targetFramed=true;targetFill=0;targetDistance=0;handFramed=true;handProfile=true;handLogged=false;handDistance=0;handNdcX=0;handNdcY=0;duelFacing=true;closeupProfile=true;visualTarget=null;visualTargetType=null;opponent=null;
            PlanetaryTouch.reset();PlanetaryLock.reset();
            actor=new RemotePlayer(mc.level,source.getGameProfile()){
                @Override public ResourceLocation getSkinTextureLocation(){
                    return source.getSkinTextureLocation();
                }
                @Override public String getModelName(){return source.getModelName();}
                @Override public boolean isModelPartShown(PlayerModelPart part){return source.isModelPartShown(part);}
            };
            var dispatcher=mc.getEntityRenderDispatcher();
            renderer=new PlayerRenderer(new EntityRendererProvider.Context(dispatcher,mc.getItemRenderer(),mc.getBlockRenderer(),dispatcher.getItemInHandRenderer(),mc.getResourceManager(),mc.getEntityModels(),mc.font),source.getModelName().equals("slim"));
            var look=show.packet.look();
            if(look!=null)try{
                var type=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(look.type());var entity=type==null?null:type.create(mc.level);
                if(entity instanceof LivingEntity living){
                    opponent=living;opponent.getEntityData().assignValues(look.data());
                    for(var pair:look.equipment())opponent.setItemSlot(pair.getFirst(),pair.getSecond().copy());
                    opponent.setPos(0,0,0);opponent.setYRot(ENEMY_YAW);opponent.yRotO=ENEMY_YAW;opponent.yBodyRot=opponent.yBodyRotO=ENEMY_YAW;opponent.yHeadRot=opponent.yHeadRotO=ENEMY_YAW;
                    visualTarget=look.uuid();visualTargetType=look.type();
                }
            }catch(RuntimeException error){opponent=null;AtomicMod.LOG.warn("PLANETARY_TARGET_MODEL_UNAVAILABLE type={}",look.type(),error);}
        }
        for(var slot:EquipmentSlot.values())actor.setItemSlot(slot,source.getItemBySlot(slot).copy());
        usedStandIn|=(Object)actor!=mc.player;
        playerSkinPreserved&=actor.getSkinTextureLocation().equals(source.getSkinTextureLocation())&&actor.getModelName().equals(source.getModelName());
        actor.setMainArm(source.getMainArm());actor.setPos(0,0,0);actor.setYRot(HERO_YAW);actor.yRotO=HERO_YAW;actor.setXRot(0);actor.xRotO=0;
        actor.yBodyRot=actor.yBodyRotO=HERO_YAW;actor.yHeadRot=actor.yHeadRotO=HERO_YAW;actor.tickCount=(int)(show.age()*20);
    }
    private static void pose(HumanoidModel<?> model,LivingEntity entity,HumanoidArm arm){
        var show=show(entity);if(show==null)return;HumanoidArm sword=entity.getMainHandItem().is(AtomicMod.SWORD.get())?entity.getMainArm():entity.getMainArm().getOpposite();if(arm!=sword)return;
        double t=show.age(),gather=AtomicMath.smooth(0,3,t),focus=AtomicMath.smooth(7.5,9,t),point=AtomicMath.smooth(11.4,12.9,t),settle=AtomicMath.smooth(16.3,19.0,t);
        var held=sword==HumanoidArm.RIGHT?model.rightArm:model.leftArm;var other=sword==HumanoidArm.RIGHT?model.leftArm:model.rightArm;float sign=sword==HumanoidArm.RIGHT?1:-1;
        held.xRot=(float)((-.4-.8*gather-1.48*focus+1.05*point)*(1-settle));held.yRot=(float)(sign*(-.12+.08*point)*(1-settle));held.zRot=-sign*.10f;
        other.xRot=(float)((-.15-.6*focus-.9*point)*(1-settle));other.yRot=sign*.15f;other.zRot=-sign*.12f;
        if(entity==actor){
            model.rightLeg.xRot=.22f+(float)Math.sin(t*.8)*.035f;model.rightLeg.zRot=.075f;
            model.leftLeg.xRot=-.15f+(float)Math.sin(t*.8+1)*.04f;model.leftLeg.zRot=-.08f;
            model.body.zRot=(float)Math.sin(t*.6)*.035f;
            // The head follows the opponent's eye line, never the closeup camera.
            float opponentSize=opponent==null?1:Math.max(.2f,Math.min(1.7f,1.55f/Math.max(.2f,opponent.getBbHeight())));
            float enemyEyes=ENEMY_POSITION.y+(opponent==null?1.5f:opponent.getEyeHeight()*opponentSize);
            model.head.xRot=(float)Math.atan2(HERO_POSITION.y+actor.getEyeHeight()-enemyEyes,Math.hypot(HERO_POSITION.x-ENEMY_POSITION.x,HERO_POSITION.z-ENEMY_POSITION.z));model.head.yRot=0;
        }
    }
    private static void quad(){var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(1,-1,0).uv(1,0).endVertex();b.vertex(1,1,0).uv(1,1).endVertex();b.vertex(-1,1,0).uv(0,1).endVertex();BufferUploader.drawWithShader(b.end());}
    public static void draw(PlanetaryClient.Show show){
        double t=show.age();if(shader==null||show.opacity()<.001||t>=13.05&&t<16.3)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;copyPlayer(show);var main=mc.getMainRenderTarget();
        if(target==null||target.width!=main.width||target.height!=main.height){if(target!=null)target.destroyBuffers();target=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);}
        var before=mc.player.position();float yaw=mc.player.getYRot(),pitch=mc.player.getXRot(),body=mc.player.yBodyRot,head=mc.player.yHeadRot;
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();float[] tint=RenderSystem.getShaderColor().clone();
        Matrix4f oldView=new Matrix4f(RenderSystem.getModelViewMatrix());
        var modelView=RenderSystem.getModelViewStack();modelView.pushPose();boolean pushed=true;
        try{
            target.setClearColor(0,0,0,0);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
            modelView.setIdentity();RenderSystem.applyModelViewMatrix();
            double close=t>=8.25?1-AtomicMath.smooth(11.4,12.6,t):0;
            double hand=AtomicMath.smooth(9.8,10.5,t)*(1-AtomicMath.smooth(11.2,12.0,t));
            double enemy=opponent!=null&&t>=6.4&&t<8.25?1:0;
            Vector3f pos=new Vector3f(HERO_POSITION).add(0,(float)Math.sin(t*.8)*.035f,0);
            // Both cameras remain on CAMERA_SIDE of the duel axis: true shot/reverse-shot.
            Vector3f wideEye=new Vector3f(pos).fma(2,DUEL_AXIS).fma(3.6f,CAMERA_SIDE).add(0,1.3f,0),wideLook=new Vector3f(0,.95f,0);
            // Stay visibly off the eyeline: a frontal camera made fixed duel facing
            // appear to break the fourth wall, despite unchanged entity yaw.
            // Hood-and-shoulders portrait: the previous 1.8 m stand-off clipped the hood and left
            // only a bare-skin forearm across the frame.
            Vector3f nearEye=new Vector3f(pos).fma(-.88f-(float)hand*.10f,DUEL_AXIS).fma(2.00f-(float)hand*.08f,CAMERA_SIDE).add(0,1.60f+(float)hand*.24f,0);
            Vector3f nearLook=new Vector3f(pos).fma(-.12f,CAMERA_SIDE).add(0,1.66f+(float)hand*.18f,0);
            Vector3f eye=wideEye.lerp(nearEye,(float)close),look=wideLook.lerp(nearLook,(float)close);
            boolean touching=PlanetaryTouch.active(t);
            if(touching){eye.set(PlanetaryTouch.eye(t));look.set(PlanetaryTouch.look());}
            float size=opponent==null?1:Math.max(.2f,Math.min(1.7f,1.55f/Math.max(.2f,opponent.getBbHeight())));
            Vector3f enemyPos=new Vector3f(ENEMY_POSITION);
            if(opponent!=null){
                // Frame the whole locked creature instead of a fixed 2 m stand-off: the old
                // distance put a cow's flank across the entire frame with no headroom.
                float extent=Math.max(opponent.getBbHeight(),opponent.getBbWidth())*size,fovRad=(float)Math.toRadians(fov(t));
                float fit=extent*.5f/((float)Math.tan(fovRad*.5)*.56f);
                // Frame the outer lock ring as well as the creature. Account for the
                // glyph's depth behind it and leave room for the slow push and letterbox.
                float lockFit=extent*(PlanetaryLock.OUTER_RADIUS+.016f)/((float)Math.tan(fovRad*.5)*.76f)-extent*.65f;
                fit=Math.max(fit,lockFit);
                // Slow 4% dolly during the shot so the locked close-up is not a frozen frame.
                double push=AtomicMath.smooth(6.65,7.85,t);
                float distance=Math.max(.95f,fit)*(float)(1-.04*push);
                targetDistance=distance;targetFill=extent/(2*distance*(float)Math.tan(fovRad*.5));
                Vector3f focus=new Vector3f(enemyPos).add(0,extent*.52f,0);
                Vector3f from=new Vector3f(focus).fma(.42f*distance,DUEL_AXIS).fma(.908f*distance,CAMERA_SIDE).add(0,extent*.10f,0);
                eye.lerp(from,(float)enemy);look.lerp(focus,(float)enemy);
                if(enemy>.8)targetFramed&=targetFill>=.25&&targetFill<=.75;
            }
            // Keep the sword beat on the same side of the duel axis as the face and target shots.
            // Crossing to -CAMERA_SIDE reverses the hero screen direction even with fixed yaw.
            Vector3f grip=null;
            if(hand>.001){
                grip=new Vector3f(pos).fma(-.34f,CAMERA_SIDE).add(0,2.02f,0);
                // The old camera sat on -CAMERA_SIDE, i.e. behind the caster's shoulder: the frame
                // showed the back of the hood with one eye slit at the edge, which reads as the
                // character facing away from the target. Keep it on the same front side as the face
                // close-up, only lower and aimed at the raised blade.
                // The solid hood projects farther than the old skin head. Leave room below
                // the reference-texture chin while keeping this camera on the same axis side.
                Vector3f handEye=new Vector3f(pos).fma(1.70f,CAMERA_SIDE).fma(-.88f,DUEL_AXIS).add(0,1.50f,0);
                Vector3f handLook=new Vector3f(pos).fma(-.10f,CAMERA_SIDE).add(0,1.94f,0);
                // Hard cut like the target beat: a blended crane passed straight over the hood
                // and produced a jarring top-down frame at ~10.2 s.
                if(hand>.5){eye.set(handEye);look.set(handLook);}
            }
            // Both settled close-ups stay on the established side of the duel axis.
            if(close>.8&&hand<.15){Vector3f offset=new Vector3f(eye).sub(pos).mul(1,0,1).normalize();closeupProfile&=Math.abs(offset.dot(DUEL_AXIS))<.60f;}
            if(hand>.92){Vector3f offset=new Vector3f(eye).sub(pos).mul(1,0,1).normalize();handProfile&=Math.abs(offset.dot(DUEL_AXIS))<.60f&&offset.dot(CAMERA_SIDE)>.80f;}
            Matrix4f view=new Matrix4f().lookAt(eye,look,new Vector3f(touching?0:.035f,1,0));
            if(hand>.92){
                Vector3f headPoint=new Vector3f(pos).add(0,1.72f,0);
                Matrix4f vp=new Matrix4f(new Matrix4f().perspective((float)Math.toRadians(fov(t)),(float)main.width/main.height,.04f,64)).mul(view);
                org.joml.Vector4f clip=vp.transform(new org.joml.Vector4f(headPoint,1));
                if(Math.abs(clip.w)>.0001){handNdcX=clip.x/clip.w;handNdcY=clip.y/clip.w;}
                handDistance=eye.distance(headPoint);
                // The camera must sit on the face side (+CAMERA_SIDE); the old -0.80 test was what
                // allowed the over-the-shoulder "back of the hood" frame.
                handFramed&=Math.abs(handNdcX)<.75&&Math.abs(handNdcY)<.75&&handDistance>=1.40&&handDistance<=2.80&&new Vector3f(eye).sub(pos).dot(CAMERA_SIDE)>.80f;
                if(!handLogged){handLogged=true;AtomicMod.LOG.info("PLANETARY_HAND_SHOT ndcX={} ndcY={} distance={} sideDot={}",handNdcX,handNdcY,handDistance,new Vector3f(eye).sub(pos).dot(CAMERA_SIDE));}
            }
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.toRadians(fov(t)),(float)main.width/main.height,.04f,64),VertexSorting.DISTANCE_TO_ORIGIN);
            PoseStack pose=new PoseStack();pose.mulPoseMatrix(view);pose.last().normal().set(view).invert().transpose();pose.translate(pos.x,pos.y,pos.z);
            if(touching)PlanetaryTouch.draw(actor,renderer.getModel(),buffers,view,t,(float)main.width/main.height);
            else if(enemy<.45)renderer.render(actor,HERO_YAW,mc.getFrameTime(),pose,buffers,15728880);
            if(!touching&&opponent!=null&&t<13.05&&(enemy>.5||close<.2)){
                opponent.tickCount=(int)(t*20);PoseStack enemyPose=new PoseStack();enemyPose.mulPoseMatrix(view);enemyPose.last().normal().set(view).invert().transpose();enemyPose.translate(enemyPos.x,enemyPos.y,enemyPos.z);enemyPose.scale(size,size,size);
                mc.getEntityRenderDispatcher().getRenderer(opponent).render(opponent,ENEMY_YAW,mc.getFrameTime(),enemyPose,buffers,15728880);
                PlanetaryLock.draw(enemyPose,buffers,eye,look,opponent.getBbHeight(),opponent.getBbWidth(),t);targetVisible=true;
                if(enemy>.8&&!targetCloseSeen){targetCloseSeen=true;AtomicMod.LOG.info("PLANETARY_TARGET_CLOSEUP event={} target={} type={} fill={} distance={}",show.packet.event(),visualTarget,visualTargetType,targetFill,targetDistance);}
            }
            buffers.endBatch();
            duelFacing&=actor.yBodyRot==HERO_YAW&&actor.yHeadRot==HERO_YAW&&(opponent==null||opponent.yBodyRot==ENEMY_YAW&&opponent.yHeadRot==ENEMY_YAW);
            main.bindWrite(true);RenderSystem.setProjectionMatrix(projection,sorting);modelView.popPose();pushed=false;RenderSystem.applyModelViewMatrix();
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(()->shader);shader.setSampler("ActorColor",target.getColorTextureId());shader.getUniform("ScreenSize").set((float)main.width,(float)main.height);shader.getUniform("Opacity").set(show.opacity());shader.getUniform("Exposure").set(touching?1.05f:1.70f);quad();
            Shot current=shot(t);if(order.isEmpty()||order.get(order.size()-1)!=current)order.add(current);if(drawn.add(current))AtomicMod.LOG.info("PLANETARY_ACTOR_SHOT event={} shot={} age={} standIn=true order={}",show.packet.event(),current,t,order);
        }finally{
            // Restore all state even if a third-party equipment renderer fails.
            if(pushed){modelView.popPose();RenderSystem.applyModelViewMatrix();}
            RenderSystem.setProjectionMatrix(projection,sorting);RenderSystem.setShaderColor(tint[0],tint[1],tint[2],tint[3]);main.bindWrite(true);
            RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
            playerUntouched&=before.equals(mc.player.position())&&yaw==mc.player.getYRot()&&pitch==mc.player.getXRot()&&body==mc.player.yBodyRot&&head==mc.player.yHeadRot;
            stateRestored&=projection.equals(RenderSystem.getProjectionMatrix())&&oldView.equals(RenderSystem.getModelViewMatrix());
        }
    }
}
