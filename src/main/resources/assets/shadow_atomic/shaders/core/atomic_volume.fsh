#version 150
uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform mat4 InvProjection;
uniform mat4 InvView;
uniform vec3 CameraLocal;
uniform float Time;
uniform float FlowTime;
uniform float Phase;
uniform float Scale;
uniform float LowFlash;
uniform float VolumeSamples;
uniform vec2 ScreenSize;
in vec2 texCoord;
out vec4 fragColor;

float hash(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
float noise(vec3 p){
    vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
vec2 cylinder(vec3 ro,vec3 rd,float radius){
    float a=dot(rd.xz,rd.xz),b=dot(ro.xz,rd.xz),c=dot(ro.xz,ro.xz)-radius*radius;
    if(a<.00001)return c<0.0?vec2(0,2400):vec2(1,-1);
    float h=b*b-a*c;if(h<0.0)return vec2(1,-1);
    return vec2((-b-sqrt(h))/a,(-b+sqrt(h))/a);
}
vec2 interval(vec3 ro,vec3 rd,float radius,float maxDistance){
    vec2 h=cylinder(ro,rd,radius);h=vec2(max(0.0,h.x),min(h.y,maxDistance));
    // Extend through the crater and below the world, rather than drawing a flat bottom cap.
    if(abs(rd.y)>.00001){vec2 caps=(vec2(-900,1300)-ro.y)/rd.y;h=vec2(max(h.x,min(caps.x,caps.y)),min(h.y,max(caps.x,caps.y)));}
    return h;
}
float current(vec3 p,float u){
    // Broad anisotropic 3D currents, evaluated through the volume. No repeated UV stripes.
    vec3 q=vec3(p.x*.020,p.y*.006-u*.55,p.z*.020);
    q.xz+=vec2(sin(p.y*.005+u*.32),cos(p.y*.003-u*.21))*.22;
    return noise(q)*.72+noise(q*2.09+vec3(8,2,5))*.28;
}
void main(){
    float u=Time-7.0,flowU=FlowTime-5.0,fade=1.0-smoothstep(10.5,14.8,Time);
    float charge=max(smoothstep(.1,5.0,Time),Phase*.9)*(1.0-smoothstep(7.0,7.6,Time));
    float body=smoothstep(0.0,.32,u)*fade;
    vec2 uv=texCoord,d=uv-.5;
    float kick=exp(-max(u,0.0)*3.0)*step(0.0,u)*(1.0-LowFlash*.8);
    vec2 bend=normalize(d+vec2(.00001))*sin(length(d)*42.0-u*22.0)*kick*.004;
    vec3 scene=texture(SceneColor,clamp(uv+bend,.001,.999)).rgb;
    float depth=texture(SceneDepth,uv).r;
    vec4 view=InvProjection*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
    float maxDistance=depth>=.99999?2400.0:length(view.xyz/view.w)/Scale;
    vec4 nearRay=InvProjection*vec4(uv*2.0-1.0,1.0,1.0);
    vec3 rd=normalize((InvView*vec4(normalize(nearRay.xyz/nearRay.w),0.0)).xyz),ro=CameraLocal/Scale;
    // Returning the camera puts a caster back inside the axial source. Adapt only
    // this small head-height viewing region, so the remaining tail cannot white out
    // their normal view. The exterior column and its narrow core retain full density.
    float interior=(1.0-smoothstep(8.0,16.0,length(ro.xz)))*(1.0-smoothstep(3.0,8.0,abs(CameraLocal.y-1.6)));

    float grade=clamp(charge*.65+body*.62,0.0,.78);
    vec3 result=scene*mix(vec3(1),vec3(.75,.36,.95),grade);
    // Soft violet light fills the held spherical field. The straight geometry stays
    // legible against dark sky, including the crown; scene depth still clips the haze.
    float sphereB=dot(ro,rd),sphereH=sphereB*sphereB-dot(ro,ro)+200.0*200.0;
    if(charge>.001&&sphereH>0.0){
        float root=sqrt(sphereH),path=max(0.0,min(maxDistance,-sphereB+root)-max(0.0,-sphereB-root));
        result+=vec3(.10,.008,.25)*charge*(1.0-exp(-path*.003));
    }
    if(body>.001){
        float radius=158.0+sin(flowU*1.6)*.65;
        float closest=clamp(-dot(ro.xz,rd.xz)/max(dot(rd.xz,rd.xz),.00001),0.0,maxDistance);
        vec3 cp=ro+rd*closest;
        float radial=length(cp.xz);
        // Derivatives must not include the discontinuous scene-depth clamp: doing so
        // expands the white core into sparkling outlines along cloud/block edges.
        float axisT=max(0.0,-dot(ro.xz,rd.xz)/max(dot(rd.xz,rd.xz),.00001));
        vec3 axisPoint=ro+rd*axisT;
        // A few blocks of low-frequency displacement keep the silhouette alive without
        // turning a dense energy column into a cloud or a stack of regular bright ribs.
        radius+=(noise(vec3(axisPoint.x*.013,axisPoint.y*.009-flowU*.31,axisPoint.z*.013))-.5)*16.0;
        float aa=clamp(fwidth(length(axisPoint.xz)-radius)*.65,.5,3.0);
        // Feather the envelope over roughly ten blocks with a slow wobble. A razor
        // cylinder edge read as a rigid cone shell in the native 10.3 s frame.
        float feather=aa+24.0+14.0*noise(vec3(axisPoint.x*.006,axisPoint.y*.004+flowU*.08,axisPoint.z*.006));
        float vertical=smoothstep(-850.0,-650.0,cp.y)*(1.0-smoothstep(900.0,1300.0,cp.y));

        // Tight optical halo follows the world-space edge, and is clipped by scene depth.
        // Its brightness falls continuously outside the silhouette; it is not an outline.
        float rim=exp(-pow(abs(radial-radius)/(18.0+feather),2.0))*vertical;
        float broad=exp(-pow(radial/218.0,2.0)*2.5)*vertical;
        vec3 halo=vec3(.22,.003,.68)*(rim*.13+broad*.20)*body*(1.0-interior*.85);
        result+=halo;

        vec2 h=interval(ro,rd,radius+feather,maxDistance);
        float path=max(0.0,h.y-h.x);
        if(path>0.0){
            float silhouette=1.0-smoothstep(radius-feather*.35,radius+feather,radial);
            // Four front-to-back optical samples carry actual depth, rather than tinting
            // a cylinder with one averaged surface value. The bounded sample count also
            // avoids a noisy/jittered march and keeps the original no-texture fallback.
            int samples=int(clamp(VolumeSamples,2.0,4.0));
            float transmittance=1.0,stepLength=path/float(samples);
            vec3 radiance=vec3(0);
            for(int i=0;i<4;i++){
                if(i>=samples)break;
                vec3 p=ro+rd*(h.x+(float(i)+.5)*stepLength);
                float flow=current(p,flowU);
                float chamber=smoothstep(.26,.71,flow);
                float currentLight=smoothstep(.49,.76,flow);
                float inner=clamp(1.0-length(p.xz)/radius,0.0,1.0);
                // A narrow world-space transition changes optical depth at the surface,
                // rather than cutting constant-density material with a one-pixel mask.
                // The interior stays dense; only the outer few blocks transmit the scene.
                float shell=smoothstep(-aa,8.0+(flow-.5)*2.5,radius-length(p.xz));
                float density=mix(.72,1.30,chamber)*shell;
                float absorption=1.0-exp(-stepLength*.030*density);
                vec3 emitted=mix(vec3(.052,.001,.15),vec3(.36,.006,.82),chamber);
                emitted+=vec3(.22,.012,.25)*currentLight;
                emitted*=1.8;
                // Darker grazing paths round the mass; broad lit currents survive
                // through the depth and move upward, without whitening the envelope.
                emitted*=mix(.58,1.0,smoothstep(0.0,.55,inner));
                radiance+=transmittance*absorption*emitted;
                transmittance*=1.0-absorption;
            }
            float coverage=silhouette*vertical*body*(1.0-interior*.70);
            result=result*(1.0-coverage*(1.0-transmittance))+radiance*coverage;
            result+=halo*.16*coverage;

            // The extremely bright axial source shines through the dense violet envelope.
            vec2 coreHit=interval(ro,rd,6.0+aa,maxDistance);
            float corePath=max(0.0,coreHit.y-coreHit.x);
            float core=(1.0-exp(-corePath*.43))*vertical*body*(1.0-interior*.94);
            float coreGlow=exp(-pow(radial/15.0,1.65))*vertical*body;
            result+=vec3(.13,.025,.26)*coreGlow*(1.0-core*.45);
            result=mix(result,vec3(.995,.98,1.0),core);

        }

        // A shallow luminous pressure cloud joins the column to the ground/crater.
        // It is an ellipsoidal volume, without a visible cap or stacked disc edges.
        vec3 stretch=vec3(1,.22,1),br=(ro-vec3(0,-3,0))/stretch,bd=rd/stretch;
        float baseAt=clamp(-dot(br,bd)/dot(bd,bd),0.0,maxDistance);
        vec3 bp=ro+rd*baseAt;
        float baseDistance=length((bp-vec3(0,-3,0))/stretch);
        float base=exp(-pow(baseDistance/214.0,4.0))*body*(1.0-interior*.55);
        float baseFlow=.8+.2*noise(bp*.026+vec3(0,-flowU*.18,0));
        result+=vec3(.065,.001,.14)*base*baseFlow;
        if(depth<.99999){
            vec3 surface=ro+rd*maxDistance;
            float spill=exp(-dot(surface.xz,surface.xz)/(270.0*270.0))*exp(-abs(surface.y)/95.0)*body;
            result+=vec3(.05,.001,.10)*spill*(1.0-interior*.55);
        }
    }
    float impact=exp(-max(u,0.0)*20.0)*step(0.0,u)*.22*(1.0-LowFlash);
    result+=vec3(.35,.25,.6)*impact;
    float vignette=1.0-smoothstep(.18,.8,length((uv-.5)*vec2(ScreenSize.x/ScreenSize.y,1)));
    result*=mix(.76,1.0,vignette);
    fragColor=vec4(clamp(result,0.0,1.0),1.0);
}
