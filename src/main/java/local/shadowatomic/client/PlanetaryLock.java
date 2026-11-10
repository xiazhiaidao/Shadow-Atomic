package local.shadowatomic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Original ring-and-circuit lock drawn behind the selected creature, with an open centre. */
public final class PlanetaryLock {
    public static final float OUTER_RADIUS=1.62f;
    private record Stroke(float x,float y,float ex,float ey){}
    private static final List<Stroke> PATTERN=pattern();
    public static int closeupFrames;
    public static boolean behindTarget=true,openCentre=true,fullyFramed=true;
    public static double maxNdcX,maxNdcY;
    public static void reset(){closeupFrames=0;behindTarget=true;openCentre=true;fullyFramed=true;maxNdcX=maxNdcY=0;}
    private static void line(List<Stroke> lines,double r,double a,double s,double b){lines.add(new Stroke((float)(r*Math.cos(a)),(float)(r*Math.sin(a)),(float)(s*Math.cos(b)),(float)(s*Math.sin(b))));}
    private static List<Stroke> pattern(){
        var lines=new ArrayList<Stroke>();
        for(double r:new double[]{.58,.62,.86,.90,1.20,1.24,1.58,OUTER_RADIUS})for(int i=0;i<144;i++)line(lines,r,i*Math.PI/72,r,(i+1)*Math.PI/72);
        for(int i=0;i<24;i++){
            double a=i*Math.PI/12,turn=a+(i%2==0?1:-1)*Math.PI/36;
            line(lines,.62,a,.74,a);line(lines,.74,a,.74,turn);line(lines,.74,turn,.86,turn);
            line(lines,.90,a,1.04,a);line(lines,1.04,a,1.04,turn);line(lines,1.04,turn,1.20,turn);
            line(lines,1.24,a,1.58,a);line(lines,1.24,a+.025,1.58,a+.025);
        }
        return List.copyOf(lines);
    }
    public static void draw(PoseStack pose,MultiBufferSource buffers,Vector3f eye,Vector3f look,float height,float width,double t){
        float alpha=(float)AtomicMath.smooth(6.3,6.85,t);if(alpha<.001)return;
        Vector3f forward=new Vector3f(look).sub(eye).normalize(),right=new Vector3f(forward).cross(0,1,0).normalize(),up=new Vector3f(right).cross(forward).normalize();
        float extent=Math.max(height,width),depth=extent*.65f;
        Vector3f center=new Vector3f(forward).mul(depth).add(0,height*.52f,0);
        // The whole luminous plane is beyond the creature's bounding box along the view.
        if(t>=6.4&&t<8.25){
            closeupFrames++;behindTarget&=new Vector3f(center).sub(0,height*.52f,0).dot(forward)>=extent*.6f;
            Matrix4f vp=new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix()).mul(pose.last().pose());
            // Check the actual outer glow, including camera dolly, against the letterboxed viewport.
            for(int i=0;i<64;i++){
                double a=i*Math.PI/32;float r=(OUTER_RADIUS+.016f)*extent;
                Vector3f edge=new Vector3f(center).fma((float)Math.cos(a)*r,right).fma((float)Math.sin(a)*r,up);
                var clip=vp.transform(new org.joml.Vector4f(edge,1));
                maxNdcX=Math.max(maxNdcX,Math.abs(clip.x/clip.w));maxNdcY=Math.max(maxNdcY,Math.abs(clip.y/clip.w));
                fullyFramed&=clip.w>0&&Math.abs(clip.x/clip.w)<.88&&Math.abs(clip.y/clip.w)<.82;
            }
        }
        geometry(buffers.getBuffer(RenderType.lightning()),pose.last().pose(),center,right,up,extent,alpha,t);
    }
    public static void geometry(VertexConsumer vertices,Matrix4f matrix,Vector3f center,Vector3f right,Vector3f up,float extent,float alpha,double t){
        float turn=(float)(AtomicMath.smooth(6.3,7.0,t)*.07),c=(float)Math.cos(turn),s=(float)Math.sin(turn);
        for(var line:PATTERN){
            openCentre&=Math.hypot(line.x,line.y)>.57&&Math.hypot(line.ex,line.ey)>.57;
            Vector3f p=new Vector3f(center).fma((line.x*c-line.y*s)*extent,right).fma((line.x*s+line.y*c)*extent,up);
            Vector3f q=new Vector3f(center).fma((line.ex*c-line.ey*s)*extent,right).fma((line.ex*s+line.ey*c)*extent,up);
            Vector3f normal=new Vector3f(right).cross(up),side=new Vector3f(q).sub(p).cross(normal).normalize();
            band(vertices,matrix,p,q,side,extent*.016f,.38f,.055f,.82f,alpha*.18f);
            band(vertices,matrix,p,q,side,extent*.0034f,.60f,.19f,.95f,alpha*.78f);
        }
    }
    private static void band(VertexConsumer b,Matrix4f m,Vector3f p,Vector3f q,Vector3f side,float width,float r,float g,float blue,float alpha){
        vertex(b,m,new Vector3f(p).fma(-width,side),r,g,blue,alpha);vertex(b,m,new Vector3f(p).fma(width,side),r,g,blue,alpha);
        vertex(b,m,new Vector3f(q).fma(width,side),r,g,blue,alpha);vertex(b,m,new Vector3f(q).fma(-width,side),r,g,blue,alpha);
    }
    private static void vertex(VertexConsumer b,Matrix4f m,Vector3f p,float r,float g,float blue,float a){b.vertex(m,p.x,p.y,p.z).color(r,g,blue,a).endVertex();}
}
