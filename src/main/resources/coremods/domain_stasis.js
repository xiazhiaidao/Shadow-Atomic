// LivingTickEvent only cancels LivingEntity.tick; Mob/animal overrides would continue.
// Replace exactly the two world dispatch calls so the complete override is paused.
function initializeCoreMod() {
    var ASMAPI=Java.type('net.minecraftforge.coremod.api.ASMAPI');
    var Opcodes=Java.type('org.objectweb.asm.Opcodes');
    var MethodInsnNode=Java.type('org.objectweb.asm.tree.MethodInsnNode');
    return { domain_stasis: {
        target: { type: 'CLASS', name: 'net.minecraft.server.level.ServerLevel' },
        transformer: function(node) {
            var ticks=0,rides=0;
            for(var m=0;m<node.methods.size();m++) {
                var method=node.methods.get(m);
                for(var i=0;i<method.instructions.size();i++) {
                    var call=method.instructions.get(i);
                    if(call.getOpcode()!==Opcodes.INVOKEVIRTUAL||call.owner!=='net/minecraft/world/entity/Entity'||call.desc!=='()V')continue;
                    var hook=null;
                    if(call.name===ASMAPI.mapMethod('m_8119_')) { hook='tickEntity';ticks++; }
                    if(call.name===ASMAPI.mapMethod('m_6083_')) { hook='tickPassenger';rides++; }
                    if(hook!==null)method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,'local/shadowatomic/DomainStasis',hook,'(Lnet/minecraft/world/entity/Entity;)V',false));
                }
            }
            if(ticks!==1||rides!==1)throw new Error('Domain stasis dispatch mismatch: '+ticks+'/'+rides);
            return node;
        }
    }};
}
