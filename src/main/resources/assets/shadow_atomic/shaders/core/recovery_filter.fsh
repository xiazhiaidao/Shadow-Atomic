#version 150
uniform sampler2D Sampler0;
uniform vec2 Texel;
uniform float Style;
uniform float Amount;
in vec2 texCoord;
out vec4 fragColor;
float luminance(vec2 uv){return dot(texture(Sampler0,uv).rgb,vec3(.2126,.7152,.0722));}
void main(){
    vec3 original=texture(Sampler0,texCoord).rgb;float l=dot(original,vec3(.2126,.7152,.0722));
    vec2 px=Texel*1.5;
    float dx=luminance(texCoord+vec2(px.x,0))-luminance(texCoord-vec2(px.x,0));
    float dy=luminance(texCoord+vec2(0,px.y))-luminance(texCoord-vec2(0,px.y));
    float edge=smoothstep(.025,.18,length(vec2(dx,dy)));
    float bright=smoothstep(.43,.62,l);
    vec3 filtered=vec3(l);
    if(Style<1.5)filtered=vec3(max(edge,bright*.22));
    else if(Style<2.5)filtered=vec3(1.-max(edge,1.-smoothstep(.13,.28,l)));
    fragColor=vec4(mix(original,filtered,Amount),1.);
}
