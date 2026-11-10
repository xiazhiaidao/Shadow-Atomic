package local.shadowatomic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** One spatial contact shared by the vanilla arm and the analytic planet shader. */
public final class PlanetaryTouch {
    public static final float RADIUS=1.06f;
    private static final Vector3f NORMAL=new Vector3f(.90f,.08f,.429f).normalize();
    public static boolean approachSeen,contactSeen,retractSeen,framed=true,skinPreserved=true;
    public static double maxContactError;
    public static int renderedFrames;
    public static boolean active(double t){return t>=2.4&&t<5.35;}
    public static float progress(double t){return (float)AtomicMath.smooth(3.3,6.25,t);}
    public static Vector3f normal(){return new Vector3f(NORMAL);}
    public static Vector3f point(){return normal().mul(RADIUS);}
    public static Vector3f eye(double t){return point().add(.02f,.10f,1.04f-(float)AtomicMath.smooth(2.4,5.35,t)*.025f);}
    public static Vector3f look(){return point().add(.22f,.035f,0);}
    private static float gap(double t){return (float)(.46*(1-AtomicMath.smooth(2.42,3.3,t))+.80*AtomicMath.smooth(4.45,5.35,t));}
    public static void reset(){approachSeen=false;contactSeen=false;retractSeen=false;framed=true;skinPreserved=true;maxContactError=0;renderedFrames=0;}

    public static void draw(AbstractClientPlayer actor,PlayerModel<AbstractClientPlayer> model,
                            MultiBufferSource buffers,Matrix4f view,double t,float aspect){
        // Use the empty hand: retain the source player's dominant hand and vanilla skin UVs.
        HumanoidArm sword=actor.getMainHandItem().is(local.shadowatomic.AtomicMod.SWORD.get())?actor.getMainArm():actor.getMainArm().getOpposite();
        boolean right=sword==HumanoidArm.LEFT;
        var arm=right?model.rightArm:model.leftArm;var sleeve=right?model.rightSleeve:model.leftSleeve;
        var oldArm=arm.storePose();var oldSleeve=sleeve.storePose();boolean av=arm.visible,sv=sleeve.visible;
        float gap=gap(t);Vector3f tip=point().fma(gap,NORMAL);
        PoseStack p=new PoseStack();p.mulPoseMatrix(view);p.last().normal().set(view).invert().transpose();
        // The distal end of the actual vanilla arm is y=10/16. Its end face rests on
        // the same tangent plane as the shader contact, without moving the live player.
        // Crop the proximal sleeve beyond the right edge; keep the close-up on the palm.
        float length=1.30f,width=.82f;
        Vector3f pivot=new Vector3f(tip).fma(10f/16*length,NORMAL);
        p.translate(pivot.x,pivot.y,pivot.z);
        p.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),normal().negate()));
        p.scale(width,length,width);
        p.translate(right?1f/16:-1f/16,0,0);
        try{
            arm.setPos(0,0,0);arm.xRot=arm.yRot=arm.zRot=0;arm.visible=true;
            sleeve.copyFrom(arm);sleeve.visible=actor.isModelPartShown(right?PlayerModelPart.RIGHT_SLEEVE:PlayerModelPart.LEFT_SLEEVE);
            var vertices=buffers.getBuffer(RenderType.entityCutoutNoCull(actor.getSkinTextureLocation()));
            arm.render(p,vertices,15728880,OverlayTexture.NO_OVERLAY);sleeve.render(p,vertices,15728880,OverlayTexture.NO_OVERLAY);
            Vector3f actual=p.last().pose().transformPosition(new Vector3f(right?-1f/16:1f/16,10f/16,0));
            Vector3f expected=new Matrix4f(view).transformPosition(new Vector3f(point()));
            if(t>=3.3&&t<4.45){contactSeen=true;maxContactError=Math.max(maxContactError,actual.distance(expected));}
            if(t<3.15&&gap>.03)approachSeen=true;if(t>4.7&&gap>.03)retractSeen=true;
            Vector4f clip=new Matrix4f().perspective((float)Math.toRadians(48),aspect,.04f,64).transform(new Vector4f(expected,1));
            framed&=clip.w>0&&Math.abs(clip.x/clip.w)<.75&&Math.abs(clip.y/clip.w)<.65;
            skinPreserved&=actor.getSkinTextureLocation().equals(net.minecraft.client.Minecraft.getInstance().player.getSkinTextureLocation());renderedFrames++;
        }finally{arm.loadPose(oldArm);sleeve.loadPose(oldSleeve);arm.visible=av;sleeve.visible=sv;}
    }
}
