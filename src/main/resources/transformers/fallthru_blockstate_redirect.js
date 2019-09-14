function initializeCoreMod() {return {'fallthru_blockstate_redirect': {

    'target': {
        'type': 'METHOD',
        'class': 'net.minecraft.block.BlockState',
        'methodName': 'func_196950_a', /*onEntityCollision*/
        'methodDesc': '(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;)V'
    },

    'transformer': function(method) {

        var ASMAPI       = Java.type('net.minecraftforge.coremod.api.ASMAPI');
        var Opcodes      = Java.type('org.objectweb.asm.Opcodes');
        var Type         = Java.type('org.objectweb.asm.tree.AbstractInsnNode'); /*type constants*/
        var VarInsnNode  = Java.type('org.objectweb.asm.tree.VarInsnNode');
        var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');

        var insert, rtrn, next;
        for (var i = method.instructions.size() - 2; i >= 0; --i) { /*start at (size - 2) to skip the end label*/
            next = method.instructions.get(i);
            if (!rtrn && next.getType() === Type.LABEL) { rtrn = next; } /*the label before RETURN*/
            if (next.getType() === Type.LINE) { insert = next; } /*the first LINENUMBER and point of insertion*/
        }

        var handler = ASMAPI.listOf(
            new VarInsnNode(Opcodes.ALOAD, 1), /*worldIn*/
            new VarInsnNode(Opcodes.ALOAD, 2), /*pos*/
            new VarInsnNode(Opcodes.ALOAD, 3), /*entityIn*/
            new VarInsnNode(Opcodes.ALOAD, 0), /*this (BlockState)*/
            ASMAPI.buildMethodCall(
                'srscode/fallthru/RedirectionHandler',
                'handleCollision',
                '(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;Lnet/minecraft/block/BlockState;)Z',
                ASMAPI.MethodType.STATIC
            ),
            new JumpInsnNode(Opcodes.IFEQ, rtrn) /*if RedirectionHandler#handleCollision returns true, execute native collision handling*/
        );

        method.instructions.insert(insert, handler);

        return method;
    }
}}}
