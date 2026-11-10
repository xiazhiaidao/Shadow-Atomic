package local.shadowatomic.client;

import com.google.gson.JsonParser;
import local.shadowatomic.AtomicMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Original-language dialogue, timed from the voice cue rather than the UI locale or visual phase. */
public final class AtomicCaptions {
    public record Line(double start,double end,String text,String language){}
    public static List<Line> load(String track){
        if(!Set.of("music_theme","voice_atomic","recovery_voice").contains(track))return List.of();
        try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(new ResourceLocation(AtomicMod.ID,"voice_captions.json"))){
            var json=JsonParser.parseReader(reader).getAsJsonObject();var rows=new ArrayList<Line>();
            for(var element:json.getAsJsonArray(track)){
                var line=element.getAsJsonObject();double start=line.get("start").getAsDouble(),end=line.get("end").getAsDouble();
                if(start>=0&&end>start)rows.add(new Line(start,end,line.get("text").getAsString(),line.get("language").getAsString()));
            }
            return List.copyOf(rows);
        }catch(Exception e){AtomicMod.LOG.warn("ATOMIC_CAPTIONS_UNAVAILABLE track={}",track);return List.of();}
    }
    public static String at(List<Line> lines,double age){for(var line:lines)if(age>=line.start&&age<line.end)return line.text;return "";}
    public static void draw(GuiGraphics g,String text,int center,int y,int width,int color){
        if(text.isEmpty())return;var mc=Minecraft.getInstance();
        var lines=mc.font.split(Component.literal(text),Math.max(80,width-40));
        for(var line:lines){g.drawString(mc.font,line,center-mc.font.width(line)/2,y,color,true);y+=12;}
    }
}
