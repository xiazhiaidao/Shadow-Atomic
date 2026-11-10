#version 150
uniform sampler2D ActorMask;
uniform vec3 Tint;
in vec2 texCoord;
out vec4 fragColor;
void main(){float alpha=texture(ActorMask,texCoord).a;if(alpha<.005)discard;fragColor=vec4(Tint,alpha);}
