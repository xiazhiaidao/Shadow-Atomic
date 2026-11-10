package local.shadowatomic.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import local.shadowatomic.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Player-owned combat settings and local listening levels in the sword inspection screen. */
public final class SwordInspectionScreen extends Screen {
    public RangeBar attack,recovery,damage,music,voice;
    public EditBox attackValue,recoveryValue,damageValue,musicValue,voiceValue;
    private final Map<RangeBar,EditBox> fields=new LinkedHashMap<>();
    private boolean edited,rangeDirty,damageDirty,audioDirty,updatingText;
    private int ticks,rowHeight;
    public static long swordFrames;
    public SwordInspectionScreen(){super(Component.translatable("inspect.shadow_atomic.title"));}
    public void authoritative(AtomicNetwork.Ranges value){
        if(!edited&&attack!=null){attack.value=value.attack();recovery.value=value.recovery();damage.value=value.damage();syncText();}
    }
    private double scale(){return Math.max(35,Math.min((width-50)/1.8,240));}
    private int swordY(){return height>=260?80:53;}
    @Override protected void init(){
        int a=attack==null?SwordInspectionClient.ranges.attack():attack.value;
        int r=recovery==null?SwordInspectionClient.ranges.recovery():recovery.value;
        int d=damage==null?SwordInspectionClient.ranges.damage():damage.value;
        fields.clear();int gap=28,margin=22,w=(width-margin*2-gap)/2,top=Math.max(78,(int)(height*.35));
        rowHeight=Math.max(25,(height-top-30)/3);int right=margin+w+gap;
        attack=bar(margin,top,w,0xffb584ef,"attack_label",10,200,a,0);
        recovery=bar(margin,top+rowHeight,w,0xff65d7ac,"recovery_label",10,200,r,0);
        damage=bar(margin,top+rowHeight*2,w,0xffe6bc76,"damage_label",-1000,1000,d,1);
        music=bar(right,top,w,0xff8daef1,"music_label",0,100,AtomicClientConfig.MUSIC_VOLUME.get(),2);
        voice=bar(right,top+rowHeight,w,0xffdf9ccf,"voice_label",0,100,AtomicClientConfig.VOICE_VOLUME.get(),3);
        attackValue=fields.get(attack);recoveryValue=fields.get(recovery);damageValue=fields.get(damage);musicValue=fields.get(music);voiceValue=fields.get(voice);
        setInitialFocus(attack);
    }
    private RangeBar bar(int x,int y,int width,int color,String label,int min,int max,int value,int kind){
        var bar=addRenderableWidget(new RangeBar(x,y+17,width,color,label,min,max,value,kind));
        var field=new EditBox(font,x+width-49,y-1,46,14,bar.getMessage());
        field.setMaxLength(5);field.setBordered(false);field.setTextColor(0xfff1edf7);
        field.setFilter(s->s.matches(min<0?"-?[0-9]{0,4}":"[0-9]{0,4}"));field.setValue(Integer.toString(value));
        field.setResponder(s->{
            if(updatingText||s.isEmpty()||s.equals("-"))return;
            int n=Integer.parseInt(s);if(n>=min&&n<=max){updatingText=true;bar.change(n);updatingText=false;}
        });
        fields.put(bar,addRenderableWidget(field));return bar;
    }
    private void syncText(){if(updatingText)return;updatingText=true;fields.forEach((bar,field)->field.setValue(Integer.toString(bar.value)));updatingText=false;}
    private void commit(EditBox field,RangeBar bar){String text=field.getValue();if(!text.isEmpty()&&!text.equals("-"))bar.change(Integer.parseInt(text));syncText();}
    private void send(){
        if(minecraft.player!=null&&ShadowGuard.holding(minecraft.player)){
            if(rangeDirty){AtomicNetwork.setRanges(attack.value,recovery.value);rangeDirty=false;}
            if(damageDirty){AtomicNetwork.setSwordDamage(damage.value);damageDirty=false;}
        }
    }
    private void saveAudio(){if(audioDirty){AtomicClientConfig.SPEC.save();audioDirty=false;}}
    @Override public void tick(){fields.values().forEach(EditBox::tick);if(++ticks%4==0)send();}
    @Override public void onClose(){fields.forEach((bar,field)->{if(field.isFocused())commit(field,bar);});send();saveAudio();super.onClose();}
    @Override public void removed(){saveAudio();super.removed();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(SwordInspectionClient.INSPECT.matches(key,scan)){onClose();return true;}
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){
            for(var entry:fields.entrySet())if(entry.getValue().isFocused()){commit(entry.getValue(),entry.getKey());setFocused(entry.getKey());return true;}
        }
        return super.keyPressed(key,scan,mods);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        fields.forEach((bar,field)->{if(field.isFocused()&&!field.isMouseOver(mx,my))commit(field,bar);});
        return super.mouseClicked(mx,my,button);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fillGradient(0,0,width,height/2,0xff141723,0xff30364a);g.fillGradient(0,height/2,width,height,0xff30364a,0xff191d2a);
        g.fill(22,15,25,24,0xffb584ef);g.drawString(font,title,32,15,0xffeeeaf5,false);
        var exit=Component.translatable("inspect.shadow_atomic.exit_hint",SwordInspectionClient.INSPECT.getTranslatedKeyMessage());
        g.drawString(font,exit,width-22-font.width(exit),15,0xffa5adc2,false);
        if(height>=260)g.drawString(font,Component.translatable("inspect.shadow_atomic.subtitle"),32,32,0xff939bb2,false);
        var pose=g.pose();g.flush();pose.pushPose();
        try{
            double s=scale();pose.translate(width/2.0-.535*s,swordY(),200);pose.scale((float)s,(float)-s,(float)s);
            pose.mulPose(Axis.ZP.rotationDegrees(-90));pose.mulPose(Axis.YP.rotationDegrees(18));Lighting.setupFor3DItems();
            RenderSystem.setShaderLights(new Vector3f(-.35f,-.8f,.8f).normalize(),new Vector3f(.6f,.3f,.5f).normalize());
            minecraft.getItemRenderer().renderStatic(new ItemStack(AtomicMod.SWORD.get()),ItemDisplayContext.FIXED,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,pose,g.bufferSource(),minecraft.level,0);
            g.flush();swordFrames++;
        }finally{pose.popPose();Lighting.setupFor3DItems();}
        pose.pushPose();pose.translate(0,0,400);
        fields.forEach((bar,field)->heading(g,bar,field));super.render(g,mx,my,partial);
        int hintY=damage.getY()-17;
        String key=damage.value<0?"heal_hint":damage.value==0?"zero_hint":"damage_hint";
        g.drawWordWrap(font,Component.translatable("inspect.shadow_atomic."+key,Math.abs(damage.value)),music.getX(),hintY,music.getWidth(),damage.value<0?0xff82dfb8:0xffabb6ce);
        if(rowHeight>=48)g.drawWordWrap(font,Component.translatable("inspect.shadow_atomic.audio_hint"),music.getX(),hintY+24,music.getWidth(),0xff8190ab);
        g.drawCenteredString(font,Component.translatable("inspect.shadow_atomic.controls"),width/2,height-15,0xff9ba8c2);
        pose.popPose();
    }
    private void heading(GuiGraphics g,RangeBar bar,EditBox field){
        int x=bar.getX(),y=bar.getY()-17,w=bar.getWidth();int color=bar==damage&&bar.value<0?0xff65d7ac:bar.color;
        g.fill(x,y+3,x+3,y+9,color);g.drawString(font,Component.translatable("inspect.shadow_atomic."+bar.label),x+9,y+1,color,false);
        g.fill(field.getX()-5,y-4,x+w+2,y+13,0xff23293b);g.fill(field.getX()-3,y+12,x+w,y+13,field.isFocused()?color:0xff59657d);
    }
    public final class RangeBar extends AbstractWidget {
        public int value;public final int min,max;private final int color,kind;private final String label;
        RangeBar(int x,int y,int w,int color,String label,int min,int max,int value,int kind){
            super(x,y,w,10,Component.translatable("inspect.shadow_atomic."+label));this.color=color;this.label=label;this.min=min;this.max=max;this.value=value;this.kind=kind;
        }
        public void change(int n){
            n=Math.max(min,Math.min(max,n));
            if(value!=n){
                if(kind==0)rangeDirty=true;else if(kind==1)damageDirty=true;
                else{if(kind==2)AtomicClientConfig.MUSIC_VOLUME.set(n);else AtomicClientConfig.VOICE_VOLUME.set(n);audioDirty=true;}
            }
            value=n;edited=true;setMessage(Component.translatable("inspect.shadow_atomic."+label).append(": "+value));syncText();
        }
        private void at(double mx){change(min+(int)Math.round((mx-getX())/getWidth()*(max-min)));}
        @Override public void onClick(double mx,double my){at(mx);}
        @Override protected void onDrag(double mx,double my,double dx,double dy){at(mx);}
        @Override public boolean mouseScrolled(double mx,double my,double delta){if(delta==0||(!isMouseOver(mx,my)&&!isFocused()))return false;change(value+(delta>0?1:-1)*(hasShiftDown()?10:1));return true;}
        @Override public boolean keyPressed(int key,int scan,int mods){
            if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT){change(value+(key==GLFW.GLFW_KEY_RIGHT?1:-1)*(hasShiftDown()?10:1));return true;}
            if(key==GLFW.GLFW_KEY_HOME){change(min);return true;}if(key==GLFW.GLFW_KEY_END){change(max);return true;}return false;
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            int x=getX(),y=getY()+3,w=getWidth(),fill=Math.round((value-min)/(float)(max-min)*w),c=kind==1&&value<0?0xff65d7ac:color;
            g.fill(x,y-1,x+w,y+3,0xff161c2b);g.fill(x,y,x+w,y+2,0xff53617b);g.fill(x,y,x+fill,y+2,c);
            int at=x+fill;g.fill(at-2,y-3,at+3,y+5,isFocused()||isHovered()?0xfff4edff:c);
            if(rowHeight>=42){
                String lo=min+(kind>=2?"%":""),hi=max+(kind>=2?"%":kind==0?" 格":"");
                g.drawString(font,lo,x,y+10,0xff8090ac,false);g.drawString(font,hi,x+w-font.width(hi),y+10,0xff8090ac,false);
                if(min<0)g.drawCenteredString(font,"0",x+w/2,y+10,0xff8090ac);
            }
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
    }
}
