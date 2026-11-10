package local.shadowatomic.client;

import local.shadowatomic.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Own-player state only. Cooldown progress is the last authoritative server snapshot. */
@Mod.EventBusSubscriber(modid=AtomicMod.ID,value=Dist.CLIENT)
public final class AtomicHud {
    private static AtomicNetwork.Status status;
    private static ResourceLocation dimension;
    public static AtomicNetwork.Status snapshot(){return status;}
    public static void clear(){status=null;dimension=null;}
    public static void update(AtomicNetwork.Status value){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.player.isDeadOrDying()||!mc.level.dimension().location().equals(value.dimension()))return;
        if(status!=null&&value.serverTick()<status.serverTick())return;
        status=value;dimension=value.dimension();
    }
    private static boolean sword(){var p=Minecraft.getInstance().player;return p!=null&&(p.getMainHandItem().is(AtomicMod.SWORD.get())||p.getOffhandItem().is(AtomicMod.SWORD.get()));}
    public static boolean visible(){
        var mc=Minecraft.getInstance();
        if(status==null||mc.level==null||mc.player==null||mc.player.isDeadOrDying()||mc.player.isSpectator()||mc.options.hideGui||mc.screen!=null||AtomicClient.cameraActive())return false;
        if(!mc.level.dimension().location().equals(status.dimension()))return false;
        if(!sword()&&status.phase()!=AtomicNetwork.Phase.HOLDING&&status.phase()!=AtomicNetwork.Phase.RELEASING)return false;
        return AtomicClient.SHOWS.stream().noneMatch(s->s.local()&&s.packet.releasing()&&s.age(0)>=6.8&&s.age(0)<8.5);
    }
    public static float progress(){return status==null?0:1-Math.max(0,Math.min(300,status.remainingTicks()))/300f;}
    public static Component title(){
        if(status==null)return Component.empty();
        var key=status.mode()==AtomicMode.PREVIEW&&!sword()?AtomicClient.REPLAY:AtomicClient.SKILL;
        return switch(status.phase()){
            case READY->Component.translatable("hud.shadow_atomic.ready",key.getTranslatedKeyMessage());
            case HOLDING->Component.translatable("hud.shadow_atomic.held",key.getTranslatedKeyMessage());
            case RELEASING->Component.translatable("hud.shadow_atomic.releasing");
            case COOLDOWN->Component.translatable("hud.shadow_atomic.cooldown",(int)Math.floor(progress()*100));
            case WORLD_BUSY->Component.translatable("hud.shadow_atomic.world_busy");
        };
    }
    public static List<Component> hints(){
        if(status==null)return List.of();var mc=Minecraft.getInstance();var rows=new ArrayList<Component>();
        var show=AtomicClient.SHOWS.stream().filter(AtomicClient.Show::local).findFirst().orElse(null);
        int radius=show==null?SwordInspectionClient.ranges.attack():Math.round(show.packet.scale()*200);
        rows.add(Component.translatable("hud.shadow_atomic.range_mode",radius,Component.translatable("mode.shadow_atomic.short."+status.mode().name().toLowerCase(Locale.ROOT))));
        if(status.phase()==AtomicNetwork.Phase.HOLDING)rows.add(Component.translatable("hud.shadow_atomic.stasis",DomainStasisClient.nearbyCount()));
        else if(status.phase()==AtomicNetwork.Phase.COOLDOWN)rows.add(Component.translatable("hud.shadow_atomic.remaining",(status.remainingTicks()+19)/20));
        else if(status.phase()==AtomicNetwork.Phase.RELEASING)rows.add(Component.translatable("hud.shadow_atomic.release_hint"));
        Component planet=PlanetaryClient.status==null?Component.translatable("hud.shadow_atomic.syncing"):PlanetaryClient.status.remainingTicks()>0?Component.translatable("hud.shadow_atomic.seconds",(PlanetaryClient.status.remainingTicks()+19)/20):Component.translatable("hud.shadow_atomic.aim");
        rows.add(Component.translatable("hud.shadow_atomic.skills",AtomicClient.PLANETARY.getTranslatedKeyMessage(),planet,AtomicClient.RECOVERY.getTranslatedKeyMessage()));
        rows.add(Component.translatable("hud.shadow_atomic.tools",ShadowPowersClient.FLIGHT.getTranslatedKeyMessage(),SwordInspectionClient.INSPECT.getTranslatedKeyMessage()));
        if(mc.player.getAbilities().flying)rows.add(Component.translatable("hud.shadow_atomic.flight",mc.options.keyJump.getTranslatedKeyMessage(),mc.options.keyShift.getTranslatedKeyMessage(),mc.options.keySprint.getTranslatedKeyMessage()));
        else rows.add(Component.translatable("hud.shadow_atomic.combat",mc.options.keyAttack.getTranslatedKeyMessage(),mc.options.keyUse.getTranslatedKeyMessage()));
        return rows;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.player.isDeadOrDying()||dimension!=null&&!dimension.equals(mc.level.dimension().location()))clear();
    }
    @SubscribeEvent public static void draw(RenderGuiEvent.Post event){
        if(PlanetaryClient.own()!=null||RecoveryClient.own()!=null)return;
        if(!visible())return;var mc=Minecraft.getInstance();var g=event.getGuiGraphics();
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        boolean compact=h<280;var content=hints();
        if(compact){
            var reduced=new ArrayList<Component>();reduced.add(content.get(0));
            if(status.phase()==AtomicNetwork.Phase.COOLDOWN||status.phase()==AtomicNetwork.Phase.HOLDING||status.phase()==AtomicNetwork.Phase.RELEASING)reduced.add(content.get(1));
            reduced.add(Component.translatable("hud.shadow_atomic.compact",AtomicClient.PLANETARY.getTranslatedKeyMessage(),AtomicClient.RECOVERY.getTranslatedKeyMessage(),ShadowPowersClient.FLIGHT.getTranslatedKeyMessage(),SwordInspectionClient.INSPECT.getTranslatedKeyMessage()));
            if(mc.player.getAbilities().flying)reduced.add(content.get(content.size()-1));
            content=reduced;
        }
        int wanted=mc.font.width(title());for(var hint:content)wanted=Math.max(wanted,mc.font.width(hint));
        int width=Math.min(Math.min(262,w-16),Math.max(180,wanted+18)),left=8;
        var titleLines=mc.font.split(title(),width-18);var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for(var hint:content)lines.addAll(mc.font.split(hint,width-18));
        int height=13+titleLines.size()*11+lines.size()*11;
        int top=compact?8:Math.max(8,h-66-height);
        int color=status.phase()==AtomicNetwork.Phase.WORLD_BUSY?0xffdba66e:0xffdcadff;
        g.fill(left,top,left+width,top+height,0xb8100d19);g.fill(left,top,left+2,top+height,color);
        int y=top+6;
        for(var line:titleLines){g.drawString(mc.font,line,left+9,y,color,false);y+=11;}
        for(var line:lines){g.drawString(mc.font,line,left+9,y,0xffc8bed8,false);y+=11;}
        g.fill(left+8,top+height-4,left+width-8,top+height-2,0xff33243f);
        float fill=status.phase()==AtomicNetwork.Phase.HOLDING||status.phase()==AtomicNetwork.Phase.RELEASING?1:progress();
        g.fill(left+8,top+height-4,left+8+Math.round((width-16)*fill),top+height-2,color);
    }
}
