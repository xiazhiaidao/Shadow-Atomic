#version 150
uniform float Time;
uniform float Cover;
uniform float Aspect;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec2 p=(texCoord-.5)*vec2(Aspect,1.0);
    float stream=sin(p.x*3.2+sin(p.y*2.1-Time*.7)*.55+Time*.2)*.5+.5;
    float broad=sin(p.x*7.0-p.y*1.1+Time*.6)*.5+.5;
    vec3 color=mix(vec3(.035,.001,.12),vec3(.24,.008,.55),stream*.6+broad*.25);
    color+=vec3(.08,.01,.14)*exp(-p.x*p.x*10.0);
    fragColor=vec4(color,Cover);
}
