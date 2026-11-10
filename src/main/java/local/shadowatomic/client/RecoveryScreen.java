package local.shadowatomic.client;

import local.shadowatomic.AtomicNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Non-pausing owner event selection. Server validates the selected UUID again. */
public final class RecoveryScreen extends Screen {
    public final AtomicNetwork.RecoveryCatalog catalog;public int selected=-1;private int page;private Button cast;
    public RecoveryScreen(AtomicNetwork.RecoveryCatalog p){super(Component.translatable("recovery.shadow_atomic.title"));catalog=p;}
    @Override protected void init(){
        int w=Math.min(380,width-28),x=(width-w)/2,y=Math.max(54,height/2-77);int perPage=4;
        for(int i=0;i<perPage;i++){int at=page*perPage+i;if(at>=catalog.entries().size())break;var e=catalog.entries().get(at);
            String location=(int)e.origin().x+", "+(int)e.origin().y+", "+(int)e.origin().z;
            Component label=Component.translatable("recovery.shadow_atomic.entry",at+1,location,e.blocks(),e.entities());
            var button=Button.builder(label,b->{selected=at;cast.active=true;}).bounds(x,y+i*28,w,24).build();addRenderableWidget(button);
        }
        int bottom=y+120;
        if(catalog.entries().size()>4){addRenderableWidget(Button.builder(Component.literal("‹"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(x,bottom,24,20).build());addRenderableWidget(Button.builder(Component.literal("›"),b->{page=Math.min((catalog.entries().size()-1)/4,page+1);rebuildWidgets();}).bounds(x+28,bottom,24,20).build());}
        cast=Button.builder(Component.translatable("recovery.shadow_atomic.cast"),b->submit()).bounds(width/2-58,bottom,116,20).build();cast.active=selected>=0;addRenderableWidget(cast);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x+w-60,bottom,60,20).build());
    }
    public void submit(){if(selected<0||selected>=catalog.entries().size())return;AtomicNetwork.requestRecovery(catalog.entries().get(selected).event());onClose();}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_ENTER&&selected>=0){submit();return true;}return super.keyPressed(key,scan,mods);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);int x=width/2;g.drawCenteredString(font,title,x,24,0xfff4e6b3);
        g.drawCenteredString(font,Component.translatable("recovery.shadow_atomic.scope",SwordInspectionClient.ranges.recovery()),x,39,0xffa9dace);
        if(catalog.entries().isEmpty())g.drawCenteredString(font,Component.translatable(catalog.message().isEmpty()?"recovery.shadow_atomic.empty":catalog.message()),x,height/2-12,0xffc5d5d0);
        if(selected>=0){var e=catalog.entries().get(selected);g.drawCenteredString(font,Component.translatable("recovery.shadow_atomic.selected",selected+1,e.recovered(),e.revived()),x,Math.max(54,height/2-77)+108,0xffe8dba7);}
        super.render(g,mx,my,partial);
    }
}
