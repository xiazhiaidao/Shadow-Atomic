// Keep vanilla renderers, armor and both hands. Only this client's skin/model selection changes.
function initializeCoreMod() {
    var ASMAPI=Java.type('net.minecraftforge.coremod.api.ASMAPI');
    var O=Java.type('org.objectweb.asm.Opcodes');
    var List=Java.type('org.objectweb.asm.tree.InsnList');
    var Insn=Java.type('org.objectweb.asm.tree.InsnNode');
    var Var=Java.type('org.objectweb.asm.tree.VarInsnNode');
    var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
    return {vanilla_steve:{
        target:{type:'CLASS',name:'net.minecraft.client.player.AbstractClientPlayer'},
        transformer:function(node){
            var matched=0;
            for(var m=0;m<node.methods.size();m++){
                var method=node.methods.get(m),hook=null;
                if(method.name===ASMAPI.mapMethod('m_108560_'))hook='skin';
                if(method.name===ASMAPI.mapMethod('m_108564_'))hook='model';
                if(method.name===ASMAPI.mapMethod('m_108561_'))hook='cape';
                if(hook===null)continue;
                matched++;
                var type=hook==='model'?'Ljava/lang/String;':'Lnet/minecraft/resources/ResourceLocation;';
                var returns=method.instructions.toArray();
                for(var i=0;i<returns.length;i++)if(returns[i].getOpcode()===O.ARETURN){
                    var code=new List();code.add(new Var(O.ALOAD,0));code.add(new Insn(O.SWAP));
                    code.add(new Call(O.INVOKESTATIC,'local/shadowatomic/client/VanillaSteve',hook,'(Lnet/minecraft/client/player/AbstractClientPlayer;'+type+')'+type,false));
                    method.instructions.insertBefore(returns[i],code);
                }
            }
            if(matched!==3)throw new Error('Vanilla Steve skin method mismatch: '+matched);
            return node;
        }
    }};
}
