#version 150
uniform sampler2D EarthDay;
uniform sampler2D EarthNight;
uniform float Time,Opacity,Aspect,LowFlash,Detail,Focal;
uniform vec3 TouchEye,TouchLook,TouchNormal;
uniform float TouchShot,ShieldProgress;
in vec2 texCoord;
out vec4 fragColor;
const float PI=3.14159265359;
const vec3 VIOLET=vec3(.42,.12,1.);
// A single world-space axis also projects to one line during the camera dolly.
const vec3 SYSTEM_AXIS=vec3(1.,.37,.16);
const vec3 STAR_POSITION=SYSTEM_AXIS*-4.1;
const float STAR_RADIUS=3.0;
const float PLANET_SCALE=.62;
float ease(float a,float b,float t){return smoothstep(a,b,t);}
float hash(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
float noise(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);}
float fbm(vec3 p){float v=0.,a=.55;for(int i=0;i<4;i++){if(i>1&&Detail<.5)break;v+=a*noise(p);p=p*2.03+vec3(4.7,9.2,3.1);a*=.48;}return v;}
vec2 sphere(vec3 ro,vec3 rd,vec3 c,float r){vec3 q=ro-c;float b=dot(q,rd),h=b*b-dot(q,q)+r*r;if(h<0.)return vec2(-1);float d=sqrt(h);return vec2(-b-d,-b+d);}
mat3 camera(vec3 ro,vec3 target){vec3 f=normalize(target-ro),r=normalize(cross(f,vec3(0,1,0)));return mat3(r,cross(r,f),f);}
vec3 rotateEarth(vec3 n){float a=-.35+Time*.013;mat2 rot=mat2(cos(a),-sin(a),sin(a),cos(a));n.xz=rot*n.xz;return n;}
vec2 globeUV(vec3 n){return vec2(atan(n.z,n.x)/(2.*PI)+.5,.5-asin(clamp(n.y,-1.,1.))/PI);}
vec3 linearColor(vec3 c){return pow(max(c,vec3(0)),vec3(2.2));}
// Bounded exposure retains cloud highlights and the terminator during the light wave.
vec3 displayColor(vec3 c){c=max(c,vec3(0));c=(c*(2.51*c+.03))/(c*(2.43*c+.59)+.14);return pow(clamp(c,0.,1.),vec3(1./2.2));}
float clouds(vec3 n){vec3 p=n*11.+vec3(Time*.006,0,Time*.004);return ease(.48,.72,fbm(p+vec3(fbm(n*3.))*2.5));}
vec3 earth(vec3 n,vec3 rd,vec3 sun,float flash){
    vec3 rn=rotateEarth(n);vec2 uv=globeUV(rn);
    // Explicit gradients prevent the longitude seam from enlarging the footprint.
    vec2 dx=dFdx(uv),dy=dFdy(uv);dx.x-=round(dx.x);dy.x-=round(dy.x);
    vec3 albedo=linearColor(textureGrad(EarthDay,uv,dx,dy).rgb);
    float light=dot(n,sun),daylight=ease(-.12,.16,light);
    float shadow=clouds(rotateEarth(normalize(n+sun*.018)))*.27;
    vec3 color=albedo*(.007+.86*max(light,0.)*(1.-shadow));
    color+=linearColor(textureGrad(EarthNight,uv,dx,dy).rgb)*vec3(1.,.72,.38)*.75*(1.-daylight);
    float sea=ease(.018,.10,albedo.b-max(albedo.r,albedo.g)*.9);
    color+=vec3(.44,.59,.8)*pow(max(0.,dot(reflect(-sun,n),-rd)),95.)*sea*.35;
    color=mix(color,vec3(.68,.74,.8)*(.015+.85*max(light,0.)),clouds(rn)*.74);
    float limb=pow(1.-max(0.,dot(n,-rd)),5.);
    color+=vec3(.018,.10,.34)*limb*daylight;
    color+=flash*vec3(.24,.13,.48)*(.08+.92*max(0.,dot(n,normalize(vec3(1,.5,1)))));
    return color;
}
vec3 atmosphere(vec3 ro,vec3 rd,vec3 center,float radius,vec3 sun,vec2 ground){
    // Thin, day-side scattering, rather than an opaque blue disc around Earth.
    vec2 h=sphere(ro,rd,center,radius*1.016);if(h.y<=0.)return vec3(0);
    float near=max(0.,h.x),far=ground.x>0.?ground.x:h.y;
    float depth=max(0.,far-near)/radius;vec3 n=normalize(ro+rd*(near+far)*.5-center);
    float lit=ease(-.22,.3,dot(n,sun)),scatter=1.-exp(-depth*3.0);
    return mix(vec3(.08,.016,.007),vec3(.016,.12,.38),lit)*scatter*(.04+.96*lit);
}
vec3 stars(vec3 rd){
    vec2 sky=vec2(atan(rd.z,rd.x)/(2.*PI),asin(rd.y)/PI);
    float band=exp(-pow(dot(rd,normalize(vec3(.7,.35,-.4)))/.13,2.)),dust=fbm(rd*18.);
    vec3 color=vec3(.0005,.0007,.0016)+vec3(.005,.006,.014)*band*dust*dust;
    for(int i=0;i<3;i++){
        float scale=180.+float(i)*310.;vec2 p=sky*scale,cell=floor(p),f=fract(p)-.5;
        float h=hash(vec3(cell,float(i)*13.));vec2 offset=vec2(hash(vec3(cell,2)),hash(vec3(cell,3)))-.5;
        float footprint=max(length(dFdx(p)),length(dFdy(p))),radius=max(.02+.055*pow(h,30.),footprint*.6);
        float star=exp(-dot(f-offset*.6,f-offset*.6)/(radius*radius));
        vec3 tint=mix(vec3(.48,.64,1.),vec3(1.,.85,.68),hash(vec3(cell,7)));
        color+=tint*star*step(.979+float(i)*.007,h)*(.11+.23*pow(h,40.))/(1.+footprint*footprint*8.);
    }
    return color;
}
float shieldPattern(vec3 n){
    // Concentric circuits originate at the palm, with alternating radial bridges.
    vec3 u=normalize(cross(TouchNormal,vec3(0,1,0))),v=cross(TouchNormal,u);
    float angle=acos(clamp(dot(n,TouchNormal),-1.,1.));
    float azimuth=atan(dot(n,v),dot(n,u)),band=angle*9.,sector=azimuth*12./PI;
    float ring=abs(fract(band)-.5)/9.;
    float bridge=abs(fract(sector+mod(floor(band+.5),2.)*.5)-.5)*PI/12.*max(.08,sin(angle));
    float line=min(ring,bridge),width=max(.0015,fwidth(angle)*.72);
    return exp(-line/width)*ease(.045,.11,angle);
}
vec3 orbital(vec2 p){
    float pull=ease(1.7,6.2,Time);if(Time>16.)pull=1.;
    float heroClose=ease(8.05,8.65,Time)*(1.-ease(11.4,12.6,Time)),enemyClose=step(6.4,Time)*(1.-step(8.05,Time));
    float close=max(heroClose,enemyClose);vec3 ro=vec3(.12,.25,mix(1.95,4.2,pull));
    float horizontal=mix(.23,.65,heroClose)-enemyClose*.88;
    vec3 rd=normalize(camera(ro,vec3(.13,0,0))*vec3(p+vec2(horizontal,mix(.05,.26,close)),Focal));
    if(TouchShot>.5){ro=TouchEye;rd=normalize(camera(ro,TouchLook)*vec3(p,Focal));}
    vec3 color=stars(rd),sun=normalize(vec3(-.82,.30,.55));vec2 hit=sphere(ro,rd,vec3(0),1.);
    float prog=ShieldProgress;
    if(hit.x>0.)color=earth(normalize(ro+rd*hit.x),rd,sun,0.);
    color+=atmosphere(ro,rd,vec3(0),1.,sun,hit);
    vec2 sh=sphere(ro,rd,vec3(0),1.06);
    if(sh.y>0.){
        vec3 n=normalize(ro+rd*max(0.,sh.x));float rim=pow(1.-max(0.,dot(n,-rd)),4.5);
        float angle=acos(clamp(dot(n,TouchNormal),-1.,1.)),reach=prog*(PI+.06);
        float sweep=1.-ease(reach-.035,reach+.035,angle);
        float front=(exp(-abs(angle-reach)/.009)*.55+exp(-abs(angle-reach)/.055)*.10)*(1.-step(.998,prog));
        float ignition=exp(-angle*angle/.008)*ease(3.3,3.38,Time)*(1.-ease(3.6,4.1,Time));
        float pattern=shieldPattern(n)*(.88+.12*sin(Time*1.7-angle*15.));
        color+=VIOLET*(sweep*(rim*.22+.005+pattern*.19)+front+ignition*.52)*step(3.3,Time)*mix(1.,.62,LowFlash);
    }
    vec3 v=vec3(1.6,.58,.5)-ro;float along=max(0.,dot(v,rd)),d=length(v-rd*along);
    float lock=ease(6.4,7.2,Time)*(1.-ease(13.2,13.4,Time));color+=VIOLET*exp(-d*d/.003)*lock*.035;
    if(Time>16.)color+=VIOLET*exp(-d*d/.12)*(1.-ease(16,19,Time))*.03;
    return color;
}
// Advected cloud belts replace identical sine-wave corrugations on every planet.
vec3 gasMaterial(vec3 n,int which){
    float lat=asin(clamp(n.y,-1.,1.)),lon=atan(n.z,n.x)+Time*(which==0?.009:.016);
    vec3 flow=vec3(cos(lon),lat*3.5,sin(lon));float warp=fbm(flow*3.2+vec3(2.1,7.7,5.9));
    float belt=lat*52.+(warp-.5)*2.0,broad=noise(vec3(belt*.8,4.2,float(which)*3.)),filaments=noise(vec3(belt*2.6,lon*7.,8.));
    vec3 dark=which==0?vec3(.30,.23,.14):vec3(.20,.13,.19),pale=which==0?vec3(.50,.41,.28):vec3(.42,.30,.40);
    vec3 color=mix(dark,pale,ease(.10,.90,broad))*(.96+.08*filaments);
    if(Detail>.5)color*=.96+.08*noise(n*160.+vec3(warp*4.));
    if(which==1){
        float wrapped=atan(sin(lon-.8),cos(lon-.8));vec2 oval=vec2(wrapped/.31,(lat+.23)/.095);float storm=exp(-dot(oval,oval));
        color=mix(color,vec3(.39,.16,.11),storm*.8);color+=vec3(.045,.026,.025)*sin(length(oval)*11.)*storm;
    }
    return color;
}
vec3 rockMaterial(vec3 n,int which){
    vec3 p=n*(which==5?18.:7.)+vec3(float(which)*11.);float ground=fbm(p),fine=noise(p*5.);
    vec3 color=which==2?mix(vec3(.16,.055,.025),vec3(.45,.23,.12),ground):mix(vec3(.12,.13,.15),vec3(.43,.44,.46),ground);
    if(Detail>.5){
        vec3 cell=floor(n*11.),f=fract(n*11.)-.5;float crater=length(f),seed=hash(cell+float(which));
        float bowl=(1.-ease(.12,.30,crater))*step(.73,seed),lip=exp(-pow((crater-.30)/.035,2.))*step(.73,seed);
        color*=1.-bowl*.30;color+=vec3(.035)*lip;
    }
    return color*(.91+.14*fine);
}
vec3 stellar(vec3 ro,vec3 rd,vec3 sky,vec2 hit){
    vec3 v=STAR_POSITION-ro;float along=dot(v,rd),distance=length(v-rd*along);
    if(hit.x>0.){
        vec3 n=normalize(ro+rd*hit.x-STAR_POSITION);
        // 0.15.0-0.17.0 replaced this with a strong limb falloff plus two noise octaves.
        // The user's verdict was that the star got worse every round (dirty brown disc),
        // so the blazing single-tone plasma look is restored verbatim.
        float grain=noise(n*(Detail>.5?85.:32.)+vec3(Time*.003));
        float plasma=fbm(n*16.+vec3(0,Time*.004,0));
        // Sunspots are small; the old 9x fBm read as cow-sized stains on the disc.
        float spots=ease(.70,.84,fbm(n*22.+vec3(4.2,1.7,9.8)));
        float limb=.43+.57*pow(max(0.,dot(n,-rd)),.45);
        vec3 star=mix(vec3(1.7,.50,.055),vec3(3.6,1.75,.42),.35+.35*plasma+.3*grain)*limb*(1.-spots*.42);
        // White-hot core keeps the disc reading as a star rather than a flat cream ball.
        star+=vec3(1.1,.85,.55)*pow(max(0.,dot(n,-rd)),10.)*.18;
        return star;
    }
    // Local corona only, leaving the central Atomic flare and planets readable.
    float halo=exp(-max(0.,distance-STAR_RADIUS)/(STAR_RADIUS*.035));
    return sky+vec3(.50,.16,.025)*halo*step(0.,along);
}
vec3 systemScene(vec2 p){
    float dolly=ease(13.05,16.3,Time);vec3 ro=vec3(.12*dolly,.07*dolly,10.-.35*dolly);
    vec3 rd=normalize(camera(ro,vec3(0,0,0))*vec3(p,1.8));
    vec2 starHit=sphere(ro,rd,STAR_POSITION,STAR_RADIUS);
    vec3 color=stellar(ro,rd,stars(rd),starHit);float best=starHit.x>0.?starHit.x:10000.;bool surface=starHit.x>0.;int which=starHit.x>0.?0:-1;
    // Just beyond Earth's protective shell, on its far side from the small moon.
    vec3 burst=SYSTEM_AXIS*2.82+normalize(SYSTEM_AXIS)*(.31*PLANET_SCALE*1.07+.10);float u=Time-13.4;
    vec3 centers[6];float radii[6];
    centers[0]=STAR_POSITION;radii[0]=STAR_RADIUS;
    centers[1]=SYSTEM_AXIS*-.30;radii[1]=.70*PLANET_SCALE;
    centers[2]=SYSTEM_AXIS*.92;radii[2]=.23*PLANET_SCALE;
    centers[3]=SYSTEM_AXIS*2.82;radii[3]=.31*PLANET_SCALE;
    centers[4]=SYSTEM_AXIS*.53;radii[4]=.16*PLANET_SCALE;
    centers[5]=SYSTEM_AXIS*2.30;radii[5]=.095*PLANET_SCALE;
    float waveRadius=max(0.,u)*9.;
    // Slot zero is the emissive foreground star, not a ringed planet.
    for(int i=1;i<6;i++){
        vec2 h=sphere(ro,rd,centers[i],radii[i]);
        if(h.x>0.&&h.x<best){
            best=h.x;which=i;surface=true;vec3 point=ro+rd*h.x,n=normalize(point-centers[i]);
            vec3 sun=normalize(STAR_POSITION-point);
            float arrival=length(point-burst)/9.,wave=ease(arrival-.04,arrival+.10,u)*(1.-ease(arrival+.24,arrival+.85,u));
            if(i==3)color=earth(n,rd,sun,wave*.7);
            else{
                vec3 material=i<2?gasMaterial(n,i):rockMaterial(n,i);float diffuse=max(0.,dot(n,sun));
                color=material*(.010+diffuse*.9);
                float rim=pow(1.-max(0.,dot(n,-rd)),4.);color+=material*rim*.025;
                color+=material*vec3(1.4,.65,2.7)*max(0.,dot(n,normalize(burst-point)))*wave*3.;
                if(i<2)color+=vec3(.025,.018,.055)*rim*ease(-.1,.25,dot(n,sun));
            }
        }
    }
    vec2 eh=sphere(ro,rd,centers[3],radii[3]);
    if(eh.y>0.&&(eh.x<0.||eh.x<=best+.02))color+=atmosphere(ro,rd,centers[3],radii[3],normalize(STAR_POSITION-centers[3]),eh);
    vec2 shield=sphere(ro,rd,centers[3],radii[3]*1.07);
    if(shield.x>0.&&shield.x<best+.04){vec3 n=normalize(ro+rd*shield.x-centers[3]);color+=VIOLET*pow(1.-max(0.,dot(n,-rd)),4.)*.16;}
    // Spherical propagation, with planet/star depths masking the luminous front.
    if(u>0.){
        vec2 front=sphere(ro,rd,burst,max(.001,waveRadius));
        if(front.y>0.){
            float distance=max(0.,front.x),fade=(1.-ease(.55,2.2,u))*ease(0.,.07,u),behind=distance>best?.04:1.;
            vec3 n=normalize(ro+rd*distance-burst);float edge=pow(1.-abs(dot(n,rd)),5.);
            color+=VIOLET*(.016+edge*.58)*fade*behind;
        }
    }
    vec3 v=burst-ro;float projection=max(0.,dot(v,rd)),d=length(v-rd*projection);
    float power=ease(-.35,0.,u)*(1.-ease(.3,1.7,u)),visible=projection<best?1.:.01;
    color+=(VIOLET*exp(-d*d/.08)*.30+vec3(.85,.65,1.)*exp(-d*d/.0018)*3.)*power*visible;
    float rays=exp(-abs(rd.y-normalize(v).y)/.0009)*exp(-d*d/2.)+exp(-abs(rd.x-normalize(v).x)/.0009)*exp(-d*d/.9);
    color+=vec3(.42,.23,.8)*rays*power*.35*visible;
    if(LowFlash<.5&&u>=0.&&u<.09){
        float outline=0.;
        if(surface){vec3 center=which==0?STAR_POSITION:centers[which];vec3 n=normalize(ro+rd*best-center);outline=pow(1.-max(0.,dot(n,-rd)),2.5);}
        return vec3(outline*2.2)+vec3(3.)*exp(-d*d/.008)*visible;
    }
    if(LowFlash<.5&&u>=.09&&u<.23)return surface?vec3(.001):vec3(3.);
    // Brief silhouette beat, followed by visible propagation and lit surface detail.
    float veil=ease(.035,.10,u)*(1.-ease(.18,.38,u))*mix(1.,.16,LowFlash);
    if(surface)color=mix(color,vec3(.001,.0004,.003),veil*.94);else color=mix(color,vec3(2.5,2.2,3.),veil);
    return color;
}
void main(){
    vec2 p=(texCoord-.5)*vec2(Aspect,1.);
    bool macro=Time>=13.05&&Time<16.3;float u=Time-13.4;
    if(macro&&u>=0.){float shock=exp(-u*8.)*(1.-LowFlash);p=p*(1.-.055*shock)+vec2(sin(u*112.),sin(u*147.))*.018*shock;}
    vec3 color=macro?systemScene(p):orbital(p);
    color=displayColor(color);color*=1.-.13*smoothstep(.3,1.2,length(p));
    if(texCoord.y<.055||texCoord.y>.945)color=vec3(.003,.001,.008);
    fragColor=vec4(color,Opacity);
}
