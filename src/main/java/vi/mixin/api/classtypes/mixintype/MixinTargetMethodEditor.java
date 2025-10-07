package vi.mixin.api.classtypes.mixintype;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.classtypes.targeteditors.TargetInsnListManipulator;
import vi.mixin.api.classtypes.targeteditors.TargetMethodManipulator;
import vi.mixin.api.injection.Vars;
import vi.mixin.api.util.TransformerHelper;

import static org.objectweb.asm.Opcodes.*;
import static vi.mixin.api.util.TransformerHelper.getLoadOpcode;

public class MixinTargetMethodEditor extends TargetMethodEditor {

    public MixinTargetMethodEditor(TargetMethodManipulator targetMethodEditors, Object annotatedEditor) {
        super(targetMethodEditors, annotatedEditor);
    }

    @SuppressWarnings("unused")
    public void makeNonFinal() {
        targetMethodEditor.makeNonFinal();
    }

    @SuppressWarnings("unused")
    public void makeNonAbstract() {
        targetMethodEditor.makeNonAbstract();
    }

    public void makePublic() {
        targetMethodEditor.makePublic();
    }

    public InsnList getCaptureLocalsInsnList(int atIndex, String targetClassName) {
        if (!(annotatedEditor instanceof MixinAnnotatedMethodEditor))
            throw new UnsupportedOperationException("capture locals is only supported when the annotated element is a method");

        try {
            MethodNode targetMethodNode = targetMethodEditor.getMethodNodeClone();
            boolean isStatic = (targetMethodNode.access & ACC_STATIC) != 0;
            int staticOffset = isStatic ? 0 : 1;

            Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
            Frame<BasicValue>[] frames = analyzer.analyze(targetClassName, targetMethodNode);

            InsnList list = new InsnList();
            Frame<BasicValue> frame = frames[atIndex];

            list.add(new TypeInsnNode(NEW, Type.getType(Vars.class).getInternalName()));
            list.add(new InsnNode(DUP));

            list.add(new LdcInsnNode(frame.getLocals() - staticOffset));
            list.add(new TypeInsnNode(ANEWARRAY, Type.getType(Object.class).getInternalName()));
            list.add(new InsnNode(DUP));

            for (int i = staticOffset; i < frame.getLocals(); i++) {
                Type localType = frame.getLocal(i).getType();

                if(i < frame.getLocals()-1) list.add(new InsnNode(DUP));
                list.add(new LdcInsnNode(i - staticOffset));
                list.add(new VarInsnNode(getLoadOpcode(localType), i));
                //box primitives
                if (localType.getSort() != Type.OBJECT && localType.getSort() != Type.ARRAY) {
                    Type boxed = TransformerHelper.getBoxedType(localType);
                    list.add(new MethodInsnNode(INVOKESTATIC, boxed.getInternalName(), "valueOf", "(" + localType.getDescriptor() + ")" + boxed.getDescriptor()));
                }
                list.add(new InsnNode(AASTORE));
            }

            list.add(new MethodInsnNode(INVOKESPECIAL, Type.getType(Vars.class).getInternalName(), "<init>", "([Ljava/lang/Object;)V"));

            return list;
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }
    }

    public TargetInsnListManipulator getInsnListEditor() {
        return targetMethodEditor.getInsnListManipulator();
    }
}
