#version 150
uniform sampler2D ActorColor;
uniform vec2 ScreenSize;
uniform float Opacity,Exposure;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    if(texCoord.y<.055||texCoord.y>.945){fragColor=vec4(0);return;}
    vec4 body=texture(ActorColor,texCoord);
    vec2 px=1.5/ScreenSize,px2=px*3.2;
    float edge=max(max(texture(ActorColor,texCoord+vec2(px.x,0)).a,texture(ActorColor,texCoord-vec2(px.x,0)).a),max(texture(ActorColor,texCoord+vec2(0,px.y)).a,texture(ActorColor,texCoord-vec2(0,px.y)).a));
    float wide=max(max(texture(ActorColor,texCoord+vec2(px2.x,0)).a,texture(ActorColor,texCoord-vec2(px2.x,0)).a),max(texture(ActorColor,texCoord+vec2(0,px2.y)).a,texture(ActorColor,texCoord-vec2(0,px2.y)).a));
    // The suit is near-black on purpose; a two-step violet rim keeps the hood and shoulders
    // readable against space instead of turning the hero into an unlit silhouette.
    float rim=max(0.,edge-body.a)*.72+max(0.,wide-edge)*.34;
    vec3 tint=mix(vec3(.70,.82,1.08),vec3(1.0,.78,1.03),texCoord.x);
    // Lift the near-black coat just enough to read its folds; the rim still owns the silhouette.
    vec3 color=body.rgb*tint*Exposure+vec3(.34,.12,.74)*rim;
    fragColor=vec4(color,clamp((body.a+rim)*Opacity,0.,1.));
}
