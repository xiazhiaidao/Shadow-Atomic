package local.shadowatomic.client;

import local.shadowatomic.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class PlanetaryClient {
    public static final Map<UUID,Show> SHOWS=new LinkedHashMap<>();
    public static AtomicNetwork.PlanetStatus status;
    private static ResourceLocation dimension;
    public static final class Show {
        public AtomicNetwork.PlanetBegin packet;private final long clock=AtomicClock.now();
        private final double initial;public final AtomicAudio.PlanetSession audio;
        public Show(AtomicNetwork.PlanetBegin p){packet=p;initial=p.elapsedMillis()/1000.0;audio=new AtomicAudio.PlanetSession(age(),p.origin(),local());}
        public double age(){return initial+(AtomicClock.now()-clock)/1e9;}
        public boolean local(){var p=Minecraft.getInstance().player;return p!=null&&p.getId()==packet.caster();}
        public float opacity(){return local()&&AtomicClient.cinema&&PlanetaryScene.available()?(float)(AtomicMath.smooth(1.2,2.3,age())*(1-AtomicMath.smooth(18.5,20,age()))):0;}
    }
    public static void begin(AtomicNetwork.PlanetBegin p){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        var existing=SHOWS.get(p.event());if(existing==null){SHOWS.put(p.event(),new Show(p));AtomicMod.LOG.info("PLANETARY_CLIENT_BEGIN event={} elapsed={} local={}",p.event(),p.elapsedMillis(),p.caster()==mc.player.getId());}else existing.packet=p;
        dimension=mc.level.dimension().location();
    }
    public static void end(UUID id){var s=SHOWS.remove(id);if(s!=null){s.audio.stop();if(s.local())PlanetaryPortrait.release();AtomicMod.LOG.info("PLANETARY_CLIENT_END event={}",id);}}
    public static Show own(){return SHOWS.values().stream().filter(Show::local).findFirst().orElse(null);}
    public static boolean macroActive(){var s=own();return s!=null&&s.opacity()>.01;}
    public static void clear(){for(var s:SHOWS.values())s.audio.stop();SHOWS.clear();status=null;dimension=null;PlanetaryPortrait.release();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.player.isDeadOrDying()||dimension!=null&&!dimension.equals(mc.level.dimension().location())){clear();return;}
        for(var s:new ArrayList<>(SHOWS.values())){if(s.age()>20.5)end(s.packet.event());else s.audio.tick(s.age());}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
    @SubscribeEvent public static void hand(RenderHandEvent event){if(macroActive())event.setCanceled(true);}
    @SubscribeEvent public static void overlay(RenderGuiOverlayEvent.Pre event){
        if(macroActive())event.setCanceled(true);
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui||RecoveryClient.own()!=null)return;var own=own();var g=event.getGuiGraphics();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        if(own!=null){
            double t=own.age();String key=t<2.4?"rise":t<6.4?"shield":t<8.4?"lock":t<13.4?"chant":t<16.3?"impact":"protected";
            String caption=own.audio.caption();String title=!caption.isEmpty()?caption:key.equals("chant")?"":Component.translatable("planet.shadow_atomic."+key).getString();
            g.drawCenteredString(mc.font,title,w/2,h-43,0xffe4ceff);
            if(!own.packet.targetName().isEmpty()&&t<13.4)g.drawCenteredString(mc.font,Component.translatable("planet.shadow_atomic.target",own.packet.targetName()),w/2,23,0xffd1a7ff);
            if(AtomicClient.cinema)g.drawString(mc.font,Component.translatable("planet.shadow_atomic.skip",AtomicClient.CAMERA.getTranslatedKeyMessage()),10,h-18,0xffb4accb);
        }else if(!AtomicHud.visible()&&(mc.player.getMainHandItem().is(AtomicMod.SWORD.get())||mc.player.getOffhandItem().is(AtomicMod.SWORD.get()))&&!AtomicClient.cameraActive()){
            if(status!=null&&status.remainingTicks()>0)g.drawCenteredString(mc.font,Component.translatable("planet.shadow_atomic.cooldown",(status.remainingTicks()+19)/20),w/2,h-104,0xffb4a0d0);
            else g.drawCenteredString(mc.font,Component.translatable("planet.shadow_atomic.ready",AtomicClient.PLANETARY.getTranslatedKeyMessage()),w/2,h-104,0xffbda6db);
        }
    }
}
