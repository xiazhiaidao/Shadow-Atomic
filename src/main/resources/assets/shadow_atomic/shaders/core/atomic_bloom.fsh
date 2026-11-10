#version 150
uniform sampler2D SceneColor;
uniform sampler2D BloomColor;
uniform vec2 Direction;
uniform float Pass;
in vec2 texCoord;
out vec4 fragColor;
vec3 sampleLight(vec2 uv){
    if(Pass>.5)return texture(BloomColor,clamp(uv,.001,.999)).rgb;
    vec3 color=texture(SceneColor,clamp(uv,.001,.999)).rgb;
    float brightness=max(color.r,max(color.g,color.b));
    return color*smoothstep(.58,.98,brightness);
}
void main(){
    vec3 glow=sampleLight(texCoord)*.227027;
    glow+=(sampleLight(texCoord+Direction*1.384615)+sampleLight(texCoord-Direction*1.384615))*.316216;
    glow+=(sampleLight(texCoord+Direction*3.230769)+sampleLight(texCoord-Direction*3.230769))*.070270;
    if(Pass<.5){fragColor=vec4(glow,1);return;}
    vec3 base=texture(SceneColor,texCoord).rgb;
    fragColor=vec4(1.0-(1.0-base)*exp(-glow*.65),1);
}
