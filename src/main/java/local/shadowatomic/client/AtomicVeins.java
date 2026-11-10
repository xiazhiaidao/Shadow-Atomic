package local.shadowatomic.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import local.shadowatomic.*;
import java.util.*;

/** Straight angular domain circuits; curved ribbons are confined to the caster's body. */
final class AtomicVeins {
    private record Line(Vec3 a,Vec3 b,double phase,boolean detail,double weight,double reveal){}
    private static final Map<Long,List<Line>> CACHE=new LinkedHashMap<>();
    static int lastSegments,lastRoots,lastForks;
    private static Vec3 point(double radial,double side,double height,double angle){return new Vec3(Math.cos(angle)*radial-Math.sin(angle)*side,height,Math.sin(angle)*radial+Math.cos(angle)*side);}
    private static void path(List<Line> out,double angle,double[][] nodes,double weight,boolean detail,double phase){
        double distance=0;
        for(int i=1;i<nodes.length;i++){
            Vec3 a=point(nodes[i-1][0],nodes[i-1][1],nodes[i-1][2],angle),b=point(nodes[i][0],nodes[i][1],nodes[i][2],angle);
            out.add(new Line(a,b,phase+distance*.09,detail,weight,Math.min(.80,a.length()/230)));distance+=a.distanceTo(b);
        }
    }
    private static double cap(double x,double z){return Math.sqrt(Math.max(0,198*198-x*x-z*z));}
    private static Vec3 bounded(Vec3 p){return p.lengthSqr()>198*198?p.normalize().scale(198):p;}
    private static void segment(List<Line> out,Vec3 a,Vec3 b,double weight,boolean detail,double phase){
        a=bounded(a);b=bounded(b);out.add(new Line(a,b,phase,detail,weight,Math.min(.80,a.length()/250)));
    }
    /** Join every column end into one irregular branching crown using the exact same vertices. */
    private static void connectCrown(List<Line> out,List<Vec3> nodes){
        int n=nodes.size();boolean[] joined=new boolean[n];double[] nearest=new double[n];int[] parent=new int[n];
        Arrays.fill(nearest,Double.POSITIVE_INFINITY);nearest[0]=0;Arrays.fill(parent,-1);
        Set<Long> edges=new HashSet<>();
        for(int step=0;step<n;step++){
            int next=-1;for(int i=0;i<n;i++)if(!joined[i]&&(next<0||nearest[i]<nearest[next]))next=i;
            joined[next]=true;
            if(parent[next]>=0){int p=parent[next];segment(out,nodes.get(p),nodes.get(next),.65,false,next*1.7);edges.add(((long)Math.min(p,next)<<32)|Math.max(p,next));lastForks++;}
            for(int i=0;i<n;i++)if(!joined[i]){double d=nodes.get(next).distanceToSqr(nodes.get(i));if(d<nearest[i]){nearest[i]=d;parent[i]=next;}}
        }
        // A few local bridges close alternate paths without forming uniform latitude cells.
        for(int i=0;i<n;i+=7){int best=-1;double distance=Double.POSITIVE_INFINITY;
            for(int j=0;j<n;j++){long edge=((long)Math.min(i,j)<<32)|Math.max(i,j);double d=nodes.get(i).distanceToSqr(nodes.get(j));if(i!=j&&!edges.contains(edge)&&d<distance){best=j;distance=d;}}
            if(best>=0){segment(out,nodes.get(i),nodes.get(best),.42,false,i*1.7+4);edges.add(((long)Math.min(i,best)<<32)|Math.max(i,best));}
        }
    }
    private static List<Line> generate(long seed){
        Random random=new Random(seed);List<Line> out=new ArrayList<>();lastRoots=0;lastForks=0;
        for(int i=0;i<20;i++){
            double angle=i*Math.PI/10+.10+(random.nextDouble()-.5)*.18,side=(random.nextDouble()-.5)*6,turn=(i%2==0?1:-1)*(7+random.nextDouble()*9),y=.18;
            path(out,angle,new double[][]{{7,side,y},{29,side,y},{42,side+turn,y},{89,side+turn,y},{104,side+turn-15,y},{158,side+turn-15,y},{180,side+turn+7,y},{196,0,y}},1.10,false,i*1.7);
            path(out,angle,new double[][]{{57,side+turn,y},{69,side+turn+12,y},{112,side+turn+12,y},{124,side+turn+24,y},{164,side+turn+24,y}},.62,true,i*1.7+3);
            lastRoots++;lastForks++;
        }
        // Episode 20's field is a curtain of long vertical strokes, with sparse angular
        // offsets. Independent columns fill a spherical volume; no latitude rings,
        // meridian grid, common polar hub or regularly tiled cells.
        List<Vec3> upper=new ArrayList<>(),lower=new ArrayList<>();
        for(int i=0;i<112;i++){
            double angle=i*2.399963+.12*(random.nextDouble()-.5),radial=Math.sqrt((i+.7)/112)*196;
            double x=Math.cos(angle)*radial,z=Math.sin(angle)*radial,h=cap(x,z);
            double kink=-h*.48+random.nextDouble()*h*.6,offset=(random.nextBoolean()?1:-1)*(5+random.nextDouble()*10);
            double dx=Math.cos(angle+.8)*offset,dz=Math.sin(angle+.8)*offset;
            Vec3 a=new Vec3(x,-h,z),b=new Vec3(x,kink,z),c=new Vec3(x+dx,kink+Math.abs(offset),z+dz);
            double endY=cap(c.x,c.z),weight=i%4==0?1.12:.48;
            Vec3 d=new Vec3(c.x,Math.max(c.y,endY-26),c.z),end=new Vec3(c.x,endY,c.z);
            segment(out,a,b,weight,false,i*1.7);segment(out,b,c,weight,false,i*1.7+2);
            segment(out,c,d,weight,false,i*1.7+3);segment(out,d,end,weight*.5,false,i*1.7+6);
            upper.add(bounded(end));lower.add(bounded(a));
            if(i%3!=0){
                Vec3 fork=c.lerp(d,.25+random.nextDouble()*.5),elbow=fork.add(-dz,Math.abs(offset),dx),tip=elbow.add(0,18+random.nextDouble()*30,0);
                segment(out,fork,elbow,.38,false,i*1.7+4);segment(out,elbow,tip,.38,false,i*1.7+5);lastForks++;
            }
            lastRoots++;
        }
        connectCrown(out,upper);connectCrown(out,lower);
        lastSegments=out.size();AtomicMod.LOG.info("ATOMIC_VEINS_VASCULAR roots={} forks={} segments={} topology=vertical_straight_curtain",lastRoots,lastForks,lastSegments);return List.copyOf(out);
    }
    static void draw(BufferBuilder b,Matrix4f matrix,long seed,double grow,double fade,double clock,Vec3 camera,double scale){
        boundary(b,matrix,camera,fade*AtomicMath.smooth(.45,.95,grow));
        List<Line> lines=CACHE.get(seed);if(lines==null){if(CACHE.size()>=8)CACHE.remove(CACHE.keySet().iterator().next());lines=generate(seed);CACHE.put(seed,lines);}
        for(Line line:lines){
            double reveal=AtomicMath.smooth(line.reveal,line.reveal+.16,grow);
            // Reveal along the actual straight segment; pulses travel without bending the geometry.
            double pulse=Math.pow(.5+.5*Math.sin(clock*1.65-line.phase),10);
            double alpha=fade*reveal*(line.detail?.34:.68)*AtomicMath.smooth(1.3,4.0,camera.distanceTo(line.a.lerp(line.b,.5))*scale);
            double range=AtomicClientConfig.QUALITY.get().detailRange;
            if(line.detail&&Double.isFinite(range))alpha*=1-AtomicMath.smooth(range*.8,range,camera.distanceTo(line.a.lerp(line.b,.5)));
            if(alpha>.001)stroke(b,matrix,line.a,line.a.lerp(line.b,reveal),camera,alpha*(.68+.32*pulse),line.weight,pulse);
        }
    }
    /** An untextured, transparent violet boundary defines the sphere without drawing a wire grid. */
    private static void boundary(BufferBuilder b,Matrix4f m,Vec3 camera,double fade){
        if(fade<.001)return;int slices=64,stacks=32;double radius=199;
        for(int j=0;j<stacks;j++)for(int i=0;i<slices;i++){
            double a=i*Math.PI*2/slices,c=(i+1)*Math.PI*2/slices,p=-Math.PI/2+j*Math.PI/stacks,q=-Math.PI/2+(j+1)*Math.PI/stacks;
            boundaryVertex(b,m,camera,a,p,radius,fade);boundaryVertex(b,m,camera,c,p,radius,fade);boundaryVertex(b,m,camera,c,q,radius,fade);boundaryVertex(b,m,camera,a,q,radius,fade);
        }
    }
    private static void boundaryVertex(BufferBuilder b,Matrix4f m,Vec3 camera,double angle,double latitude,double radius,double fade){
        Vec3 normal=new Vec3(Math.cos(latitude)*Math.cos(angle),Math.sin(latitude),Math.cos(latitude)*Math.sin(angle)),point=normal.scale(radius);
        double rim=Math.pow(1-Math.abs(normal.dot(camera.subtract(point).normalize())),3);
        double alpha=fade*(camera.length()>radius?.015+.24*rim:.10);
        vertex(b,m,point,.38,.018,.92,alpha);
    }
    static void charge(BufferBuilder b,Matrix4f matrix,double t,double alpha,Vec3 camera,double scale,double clock){
        // Inside the caster's first-person view, the body is hidden. Do not project
        // its close-up aura as giant curved lines across the straight domain field.
        if(camera.horizontalDistance()*scale<1.1&&camera.y*scale>.3&&camera.y*scale<2.8)return;
        int count=AtomicClientConfig.QUALITY.get()==AtomicClientConfig.Quality.LOW?24:48;
        double contraction=1-AtomicMath.smooth(5.5,7,t),radius=.35+.42*contraction;
        for(int strand=0;strand<3;strand++)for(int i=0;i<count;i++){
            double u=i/(double)count,v=(i+1)/(double)count;
            Vec3 a=auraPoint(u,strand,radius,clock,scale),end=auraPoint(v,strand,radius,clock,scale);
            double envelope=Math.sin(Math.PI*u);stroke(b,matrix,a,end,camera,alpha*.60*envelope,.55,1);
        }
    }
    private static Vec3 auraPoint(double u,int strand,double radius,double clock,double scale){
        double a=u*Math.PI*2.1+strand*Math.PI*2/3-clock*.35;
        return new Vec3(Math.sin(a)*radius,(.30+u*1.7)+.08*Math.sin(a),Math.cos(a)*radius).scale(1/scale);
    }
    private static void stroke(BufferBuilder b,Matrix4f matrix,Vec3 a,Vec3 end,Vec3 camera,double alpha,double weight,double pulse){
        Vec3 middle=a.lerp(end,.5),direction=end.subtract(a),side=direction.cross(camera.subtract(middle));
        if(side.lengthSqr()<1e-8)return;
        double closest=AtomicMath.clamp(camera.subtract(a).dot(direction)/Math.max(1e-8,direction.lengthSqr()));alpha*=AtomicMath.smooth(.3,1.1,camera.distanceTo(a.lerp(end,closest)));
        double width=Math.max(.028,Math.min(.30,camera.distanceTo(middle)*.0022))*weight;side=side.normalize();
        if(AtomicClientConfig.QUALITY.get().veinLayers>1)ribbon(b,matrix,a,end,side.scale(width*5.0),.42,.025,1,alpha*.13);
        ribbon(b,matrix,a,end,side.scale(width*2.0),.67,.12,1,alpha*.30);
        ribbon(b,matrix,a,end,side.scale(width),.92,.42+.2*pulse,1,alpha);
        ribbon(b,matrix,a,end,side.scale(width*.34),1,.86,1,alpha*(.7+.25*pulse));
    }
    private static void ribbon(BufferBuilder b,Matrix4f m,Vec3 a,Vec3 end,Vec3 side,double r,double g,double blue,double alpha){
        vertex(b,m,a.subtract(side),r,g,blue,alpha);vertex(b,m,end.subtract(side),r,g,blue,alpha);vertex(b,m,end.add(side),r,g,blue,alpha);vertex(b,m,a.add(side),r,g,blue,alpha);
    }
    private static void vertex(BufferBuilder b,Matrix4f m,Vec3 p,double r,double g,double blue,double alpha){b.vertex(m,(float)p.x,(float)p.y,(float)p.z).color((float)r,(float)g,(float)blue,(float)alpha).endVertex();}
}
