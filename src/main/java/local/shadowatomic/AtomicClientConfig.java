package local.shadowatomic;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only visual budgets. No gameplay parameters or client classes are loaded here. */
public final class AtomicClientConfig {
    public enum Quality {
        LOW(2,4,1,64,32,24,8,48,1,.5),
        MEDIUM(3,3,2,96,48,40,10,96,1,.75),
        HIGH(4,2,2,128,64,65,12,Double.POSITIVE_INFINITY,2,1);
        public final int opticalSamples,bloomDivisor,volumeEvents,ringSegments,columnSegments,sparks,chargeLines,veinLayers;
        public final double detailRange,meshScale;
        Quality(int opticalSamples,int bloomDivisor,int volumeEvents,int ringSegments,int columnSegments,int sparks,int chargeLines,double detailRange,int veinLayers,double meshScale){
            this.opticalSamples=opticalSamples;this.bloomDivisor=bloomDivisor;this.volumeEvents=volumeEvents;this.ringSegments=ringSegments;this.columnSegments=columnSegments;this.sparks=sparks;this.chargeLines=chargeLines;this.detailRange=detailRange;this.veinLayers=veinLayers;this.meshScale=meshScale;
        }
        public String translationKey(){return "quality.shadow_atomic."+name().toLowerCase(java.util.Locale.ROOT);}
    }
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<Quality> QUALITY;
    public static final ForgeConfigSpec.BooleanValue ENHANCED_LIGHTING;
    public static final ForgeConfigSpec.IntValue MUSIC_VOLUME,VOICE_VOLUME;
    static{
        var b=new ForgeConfigSpec.Builder();b.push("visuals");
        QUALITY=b.comment("Client visual budget only. HIGH preserves the full volume/material detail. All tiers retain every skill phase and the primary vein outline.").defineEnum("quality",Quality.HIGH);
        ENHANCED_LIGHTING=b.comment("Depth-aware volume and bloom. Fabulous automatically uses geometry fallback; disable this for other incompatible graphics/shader combinations. Gameplay is unchanged.").define("enhancedLighting",true);
        b.pop();b.push("audio");
        MUSIC_VOLUME=b.comment("本模组背景音乐音量，0–100；可在持剑检视界面调节。仍受原版主音量/玩家音量控制。").defineInRange("musicVolume",100,0,100);
        VOICE_VOLUME=b.comment("本模组原声台词音量，0–100；立即作用于正在播放的声音。").defineInRange("voiceVolume",100,0,100);
        b.pop();SPEC=b.build();
    }
}
