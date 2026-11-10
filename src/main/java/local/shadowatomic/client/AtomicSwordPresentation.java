package local.shadowatomic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import local.shadowatomic.AtomicMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Render-only poses use Forge's supported arm transformer, with no entity use-state edits. */
public final class AtomicSwordPresentation {
    private static final HumanoidModel.ArmPose ATOMIC=HumanoidModel.ArmPose.create("SHADOW_ATOMIC",true,AtomicSwordPresentation::apply);
    private static final HumanoidModel.ArmPose FLIGHT=HumanoidModel.ArmPose.create("SHADOW_FLIGHT",true,AtomicSwordPresentation::flight);
    public static final IClientItemExtensions EXTENSIONS=new IClientItemExtensions(){
        @Override public HumanoidModel.ArmPose getArmPose(LivingEntity entity,InteractionHand hand,ItemStack stack){
            if(PlanetaryPortrait.show(entity)!=null){if(hand==InteractionHand.OFF_HAND&&entity.getMainHandItem().is(AtomicMod.SWORD.get()))return null;return PlanetaryPortrait.armPose();}
            var active=show(entity);
            if(entity instanceof net.minecraft.world.entity.player.Player player&&ShadowFlight.flying(player)&&!player.isUsingItem()&&(active==null||!active.packet.releasing()))return FLIGHT;
            if(active==null)return null;
            // Main-hand takes precedence if both hands hold the casting sword.
            if(hand==InteractionHand.OFF_HAND&&entity.getMainHandItem().is(AtomicMod.SWORD.get()))return null;
            return ATOMIC;
        }
        @Override public boolean applyForgeHandTransform(PoseStack pose,LocalPlayer player,HumanoidArm arm,ItemStack stack,float partial,float equip,float swing){
            if(local.shadowatomic.ShadowCombat.blocking(player)){
                float sign=arm==HumanoidArm.RIGHT?1:-1;
                pose.translate(sign*-.22,-.28,-1.15);pose.mulPose(Axis.ZP.rotationDegrees(sign*-68));pose.mulPose(Axis.YP.rotationDegrees(sign*90));
                return true;
            }
            var show=show(player);if(show==null)return false;
            double r=show.packet.releasing()?show.elapsed(partial):0;
            float sign=arm==HumanoidArm.RIGHT?1:-1;
            double gather=show.packet.releasing()?AtomicMath.smooth(0,3.4,r):AtomicMath.smooth(0,1.3,show.elapsed(partial))*.15;
            double strike=show.packet.releasing()?AtomicMath.smooth(5,5.18,r):0;
            double fade=show.packet.releasing()?1-AtomicMath.smooth(6,7,r):1;
            pose.translate(sign*.05*gather*fade,.05*gather*fade,-.12*gather*fade);
            pose.mulPose(Axis.ZP.rotationDegrees((float)(sign*(-18*gather+48*strike)*fade)));
            pose.mulPose(Axis.XP.rotationDegrees((float)((-12*gather+20*strike)*fade)));
            return false; // Preserve vanilla equip/swing transforms and input behavior.
        }
    };
    private static void flight(HumanoidModel<?> model,LivingEntity entity,HumanoidArm arm){
        if(!(entity instanceof net.minecraft.world.entity.player.Player player))return;
        var view=ShadowFlight.view(player);if(view==null)return;float blend=view.blend(Minecraft.getInstance().getFrameTime());
        HumanoidArm sword=entity.getMainHandItem().is(AtomicMod.SWORD.get())?entity.getMainArm():entity.getMainArm().getOpposite();
        if(arm!=sword)return;
        var held=sword==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
        var other=sword==HumanoidArm.RIGHT?model.leftArm:model.rightArm;
        held.xRot=-1.45f*blend;held.yRot=0;held.zRot=(sword==HumanoidArm.RIGHT?1:-1)*.08f;
        other.xRot=-2.85f*blend;other.yRot=0;other.zRot=(sword==HumanoidArm.RIGHT?-1:1)*.08f;
    }
    public static AtomicClient.Show show(LivingEntity entity){
        if(entity.isDeadOrDying()||entity.isFallFlying()||entity.isSwimming()||entity.isSleeping()||entity.isUsingItem())return null;
        return AtomicClient.SHOWS.stream().filter(s->s.packet.caster()==entity.getId()&&(!s.packet.releasing()||s.elapsed(0)<7)).findFirst().orElse(null);
    }
    public record Pose(String stage,float swordX,float swordY,float swordZ,float otherX,float otherY,float otherZ){}
    private static float mix(float a,float b,double t){return (float)(a+(b-a)*t);}
    public static Pose pose(AtomicClient.Show show,float partial){
        double elapsed=show.elapsed(partial);
        if(!show.packet.releasing()){
            double rise=AtomicMath.smooth(0,.8,elapsed)*(1-AtomicMath.smooth(.9,1.8,elapsed));
            return new Pose(elapsed<1.8?"rise":"hold",mix(-.42f,-1.25f,rise),-.12f,-.08f,mix(-.10f,-.45f,rise),.10f,.08f);
        }
        double gather=AtomicMath.smooth(0,2.6,elapsed),focus=AtomicMath.smooth(3.5,4.7,elapsed);
        double strike=AtomicMath.smooth(5,5.18,elapsed),settle=AtomicMath.smooth(5.7,7,elapsed);
        float sx=mix(-.42f,-1.20f,gather),sy=mix(-.12f,-.32f,gather),sz=mix(-.08f,-.18f,gather);
        float ox=mix(-.10f,-1.12f,gather),oy=mix(.10f,.44f,gather),oz=.10f;
        sx=mix(sx,-2.72f,focus);sy=mix(sy,-.12f,focus);ox=mix(ox,-.85f,focus);
        sx=mix(sx,.25f,strike);sy=mix(sy,-.62f,strike);sz=mix(sz,-.26f,strike);
        ox=mix(ox,-1.12f,strike);oy=mix(oy,.65f,strike);oz=mix(oz,.25f,strike);
        return new Pose(elapsed<3.5?"gather":elapsed<5?"focus":elapsed<5.18?"strike":"follow",mix(sx,-.32f,settle),mix(sy,0,settle),mix(sz,0,settle),mix(ox,0,settle),mix(oy,0,settle),mix(oz,0,settle));
    }
    private static void apply(HumanoidModel<?> model,LivingEntity entity,HumanoidArm arm){
        var show=show(entity);if(show==null)return;
        HumanoidArm sword=entity.getMainHandItem().is(AtomicMod.SWORD.get())?entity.getMainArm():entity.getMainArm().getOpposite();
        if(arm!=sword)return;
        Pose p=pose(show,Minecraft.getInstance().getFrameTime());float sign=sword==HumanoidArm.RIGHT?1:-1;
        var held=sword==HumanoidArm.RIGHT?model.rightArm:model.leftArm;var other=sword==HumanoidArm.RIGHT?model.leftArm:model.rightArm;
        held.xRot=p.swordX;held.yRot=sign*p.swordY;held.zRot=sign*p.swordZ;
        other.xRot=p.otherX;other.yRot=sign*p.otherY;other.zRot=-sign*p.otherZ;
    }
}
