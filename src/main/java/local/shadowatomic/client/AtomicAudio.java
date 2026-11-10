package local.shadowatomic.client;

import com.google.gson.JsonParser;
import local.shadowatomic.AtomicMod;
import local.shadowatomic.AtomicClock;
import local.shadowatomic.AtomicClientConfig;
import local.shadowatomic.PlanetaryAtomic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** All tracks, including the indefinitely held background loop, belong to one event. */
public final class AtomicAudio {
    public static final class RecoverySession {
        private Cue voice;
        public RecoverySession(double age,Vec3 origin,boolean local,boolean alreadyCommitted){
            // Start once with the event. Late arrivals and completed/replayed packets never restart words.
            if(age<.35&&!alreadyCommitted){
                voice=play(AtomicMod.RECOVERY_VOICE.get(),1f,false,origin,local,1);
            }
        }
        public void stop(){if(voice!=null)voice.finish();}
        public String caption(){return voice==null?"":voice.caption();}
        public int activeTracks(){return voice!=null&&Minecraft.getInstance().getSoundManager().isActive(voice)?1:0;}
    }
    /** Independent second-skill soundtrack; late viewers never restart missed words. */
    public static final class PlanetSession {
        private Cue loop,voice,blast,tail,charge;
        private boolean voiced,blasted,tailed;
        private final Vec3 origin;private final boolean local;
        public PlanetSession(double elapsed,Vec3 origin,boolean local){
            this.origin=origin;this.local=local;voiced=elapsed>PlanetaryAtomic.VOICE_AT+.35;blasted=elapsed>PlanetaryAtomic.IMPACT_AT+.35;tailed=elapsed>PlanetaryAtomic.IMPACT_AT+1.65;
            loop=play(AtomicMod.DOMAIN_LOOP.get(),.8f,true,origin,local,1);
            if(elapsed<.5)charge=play(AtomicMod.CHARGE.get(),.4f,false,origin,local,1);
        }
        public void tick(double t){
            double at=PlanetaryAtomic.VOICE_AT,hit=PlanetaryAtomic.IMPACT_AT;
            if(t>=at&&!voiced){voiced=true;if(t<at+.35)voice=play(AtomicMod.VOICE.get(),1,false,origin,local,1);}
            if(t>=hit&&!blasted){blasted=true;if(t<hit+.35)blast=play(AtomicMod.BLAST.get(),.95f,false,origin,local,1);}
            if(t>=hit+1.3&&!tailed){tailed=true;if(t<hit+1.65)tail=play(AtomicMod.TAIL.get(),.7f,false,origin,local,1);}
            if(loop!=null)loop.gain((float)(.8*(t>=at&&t<hit+.3?.28:.8)*(1-AtomicMath.smooth(18,20,t))));
        }
        public void stop(){for(Cue c:new Cue[]{loop,voice,blast,tail,charge})if(c!=null)c.finish();}
        public String caption(){return voice==null?"":voice.caption();}
        public int activeTracks(){int n=0;for(Cue c:new Cue[]{loop,voice,blast,tail,charge})if(c!=null&&Minecraft.getInstance().getSoundManager().isActive(c))n++;return n;}
    }
    public static final class Session {
        private Cue music,loop,voice,charge,blast,tail;
        private boolean voiceTried,blasted,tailed;
        private float voiceAt=2.1f,musicVolume=.85f,voiceVolume=1f,introLoopAt=39.8f;
        private Vec3 origin;
        private final boolean local;
        private final float scale;
        Session(double elapsed,boolean releasing,Vec3 origin,boolean local,float scale){
            this.origin=origin;this.local=local;this.scale=scale;
            var mc=Minecraft.getInstance();
            try(var resource=mc.getResourceManager().getResource(new ResourceLocation(AtomicMod.ID,"audio_timing.json")).orElseThrow().open();var reader=new InputStreamReader(resource,StandardCharsets.UTF_8)){
                var json=JsonParser.parseReader(reader).getAsJsonObject();
                voiceAt=(float)Math.max(2,Math.min(6,json.get("voiceStart").getAsDouble()));
                musicVolume=(float)Math.max(0,Math.min(1,json.get("musicVolume").getAsDouble()));
                voiceVolume=(float)Math.max(0,Math.min(1.5,json.get("voiceVolume").getAsDouble()));
                if(json.has("introLoopAt"))introLoopAt=(float)Math.max(1,Math.min(120,json.get("introLoopAt").getAsDouble()));
            }catch(Exception ignored){}
            if(releasing){release(elapsed);return;}
            if(elapsed<.5){charge=play(AtomicMod.CHARGE.get(),.42f,false);music=play(AtomicMod.MUSIC.get(),musicVolume,false);}
            else startLoop();
        }
        private Cue play(SoundEvent event,float volume,boolean looping){return AtomicAudio.play(event,volume,looping,origin,local,scale);}
        private void startLoop(){if(loop==null)loop=play(AtomicMod.DOMAIN_LOOP.get(),musicVolume,true);}
        public boolean looping(){return loop!=null;}
        public int activeTracks(){int count=0;for(Cue c:new Cue[]{music,loop,voice,charge,blast,tail})if(c!=null&&Minecraft.getInstance().getSoundManager().isActive(c))count++;return count;}
        public void release(double elapsed){
            if(music!=null){music.finish();music=null;}
            if(charge!=null){charge.finish();charge=null;}
            startLoop();
            // Joining a release mid-way does not replay words or blast sounds already missed.
            voiceTried=2+elapsed>voiceAt+.3;blasted=elapsed>5.3;tailed=elapsed>6.6;
        }
        public void tick(double elapsed,boolean releasing){
            tick(elapsed,releasing,2+elapsed);
        }
        public void tick(double elapsed,boolean releasing,double visualAge){
            if(!releasing){
                if(elapsed>=introLoopAt){if(music!=null){music.finish();music=null;}startLoop();}
                if(charge!=null&&elapsed>=6){charge.finish();charge=null;}
                return;
            }
            double age=2+elapsed;
            if(!voiceTried&&age>=voiceAt){voiceTried=true;if(age<voiceAt+.35)voice=play(AtomicMod.VOICE.get(),voiceVolume,false);}
            double duck=age>=voiceAt&&age<8.5?.28:.8;
            if(loop!=null)loop.gain((float)(musicVolume*duck*(1-AtomicMath.smooth(10.5,13.5,visualAge))));
            if(age>=7&&!blasted){blasted=true;if(age<7.35)blast=play(AtomicMod.BLAST.get(),.95f,false);}
            if(age>=8.3&&!tailed){tailed=true;if(age<8.65)tail=play(AtomicMod.TAIL.get(),.7f,false);}
        }
        public void move(Vec3 value){origin=value;for(Cue cue:new Cue[]{music,loop,voice,charge,blast,tail})if(cue!=null)cue.move(value);}
        public String caption(){String text=voice==null?"":voice.caption();return !text.isEmpty()?text:music==null?"":music.caption();}
        public void stop(){for(Cue c:new Cue[]{music,loop,voice,charge,blast,tail})if(c!=null)c.finish();}
    }
    private static Cue play(SoundEvent event,float volume,boolean looping,Vec3 origin,boolean local,float scale){Cue cue=new Cue(event,volume,looping,origin,local,scale);Minecraft.getInstance().getSoundManager().play(cue);return cue;}
    private static final class Cue extends AbstractTickableSoundInstance {
        private Vec3 origin;
        private final boolean local;
        private final double near,range;
        private float baseGain;
        private final boolean musicTrack,voiceTrack;
        private final long started=AtomicClock.now();
        private final java.util.List<AtomicCaptions.Line> captions;
        Cue(SoundEvent event,float volume,boolean looping,Vec3 origin,boolean local,float scale){
            super(event,SoundSource.PLAYERS,RandomSource.create());this.origin=origin.add(0,1.6,0);this.local=local;this.baseGain=volume;
            double size=Math.max(.25,Math.min(1.5,scale));
            String name=event.getLocation().getPath();
            musicTrack=name.equals("music_theme")||name.equals("domain_loop");voiceTrack=name.equals("voice_atomic")||name.equals("recovery_voice");
            captions=AtomicCaptions.load(name);near=(voiceTrack?24:20)*size;
            range=(switch(name){case "charge"->240;case "voice_atomic","recovery_voice"->320;case "blast","tail"->900;default->600;})*size;
            this.pitch=1;this.relative=local;this.attenuation=Attenuation.NONE;this.looping=looping;this.delay=0;
            if(!local){this.x=this.origin.x;this.y=this.origin.y;this.z=this.origin.z;}
            updateGain();
        }
        private void updateGain(){
            float gain=baseGain*(musicTrack?AtomicClientConfig.MUSIC_VOLUME.get()/100f:voiceTrack?AtomicClientConfig.VOICE_VOLUME.get()/100f:1f);
            if(local){volume=gain;return;}
            var player=Minecraft.getInstance().player;
            double falloff=player==null?0:1-AtomicMath.smooth(near,range,player.getEyePosition().distanceTo(origin));
            volume=(float)(gain*falloff*falloff);
        }
        void move(Vec3 value){origin=value.add(0,1.6,0);if(!local){x=origin.x;y=origin.y;z=origin.z;}updateGain();}
        void gain(float value){baseGain=value;updateGain();}
        void finish(){stop();Minecraft.getInstance().getSoundManager().stop(this);}
        String caption(){return isStopped()?"":AtomicCaptions.at(captions,(AtomicClock.now()-started)/1e9);}
        // Keep a muted loop alive: walking back into range must not restart its music.
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick(){updateGain();}
    }
}
