package local.shadowatomic.client;

import local.shadowatomic.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class RecoveryClient {
    public static final Map<UUID,Show> SHOWS=new LinkedHashMap<>();private static long catalogAt;
    public static AtomicNetwork.RecoveryCatalog lastCatalog;
    public record Spark(Vec3 target,double start,long seed){}
    public static final class Show {
        public AtomicNetwork.RecoveryState packet;private long clock;private double elapsed;public double doneAt=-1,flashAt=-1;private boolean committedSeen;public final AtomicAudio.RecoverySession audio;
        public final List<Spark> sparks=new ArrayList<>();private final Map<Vec3,Double> sampled=new HashMap<>();
        Show(AtomicNetwork.RecoveryState p){packet=p;clock=AtomicClock.now();elapsed=p.elapsedMillis()/1000.0;audio=new AtomicAudio.RecoverySession(age(),p.origin(),local(),p.committed());update(p);}
        public double age(){return elapsed+(AtomicClock.now()-clock)/1e9;}
        public boolean local(){var p=Minecraft.getInstance().player;return p!=null&&p.getId()==packet.caster();}
        void update(AtomicNetwork.RecoveryState p){
            packet=p;elapsed=p.elapsedMillis()/1000.0;clock=AtomicClock.now();
            if(p.committed()&&!committedSeen){committedSeen=true;if(p.elapsedMillis()<RecoveryAtomic.PRELUDE*1000+1000)flashAt=age();}
            if(p.done()&&doneAt<0)doneAt=age();
            for(Vec3 at:p.samples())if(!sampled.containsKey(at)){sampled.put(at,age());if(sparks.size()<96)sparks.add(new Spark(at,age(),at.hashCode()));}
        }
    }
    public static Show own(){return SHOWS.values().stream().filter(Show::local).findFirst().orElse(null);}
    public static void input(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        if(own()!=null){AtomicNetwork.cancelRecovery();return;}
        if(catalogAt!=0&&AtomicClock.now()-catalogAt<2_000_000_000L)return;
        catalogAt=AtomicClock.now();AtomicNetwork.requestRecoveryCatalog();
    }
    public static void catalog(AtomicNetwork.RecoveryCatalog p){
        var mc=Minecraft.getInstance();lastCatalog=p;
        boolean waiting=catalogAt!=0&&AtomicClock.now()-catalogAt<5_000_000_000L;catalogAt=0;
        if(!waiting||mc.player==null||mc.player.isDeadOrDying()||mc.level==null||!p.dimension().equals(mc.level.dimension().location())||mc.screen!=null)return;
        mc.setScreen(new RecoveryScreen(p));
    }
    public static void state(AtomicNetwork.RecoveryState p){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.player.isDeadOrDying()||!p.dimension().equals(mc.level.dimension().location()))return;
        var old=SHOWS.get(p.event());if(old!=null){if(p.elapsedMillis()>=old.packet.elapsedMillis())old.update(p);return;}
        if(SHOWS.size()>=4)end(SHOWS.keySet().iterator().next());SHOWS.put(p.event(),new Show(p));
    }
    public static void end(UUID id){var s=SHOWS.remove(id);if(s!=null)s.audio.stop();if(SHOWS.isEmpty())RecoveryFilter.release();}
    public static void clear(){SHOWS.values().forEach(s->s.audio.stop());SHOWS.clear();RecoveryFilter.release();catalogAt=0;lastCatalog=null;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.player.isDeadOrDying()){clear();return;}
        for(var s:new ArrayList<>(SHOWS.values())){
            if(!s.packet.dimension().equals(mc.level.dimension().location())||s.age()>180||s.doneAt>=0&&s.age()-s.doneAt>3){end(s.packet.event());continue;}
            s.sparks.removeIf(p->s.age()-p.start()>1.6);s.sampled.entrySet().removeIf(p->s.age()-p.getValue()>4);
        }
    }
}
