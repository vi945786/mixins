package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Invoker;
import vi.mixin.api.editors.BytecodeEditor;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.MethodTransformer;
import vi.mixin.api.transformers.TransformerHelper;

import java.util.ArrayList;
import java.util.List;

import static vi.mixin.api.transformers.TransformerHelper.addLoadOpcodesOfMethod;

public class InvokerTransformer implements MethodTransformer<Invoker> {

    private static void validate(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Invoker mixinAnnotation, ClassEditor targetClassEditor) {
        String name = "@Invoker " + mixinClassEditor.getName() + "." + mixinMethodEditor.getName() + mixinMethodEditor.getDesc();
        MethodEditor target = targetClassEditor.getMethodEditor(mixinAnnotation.value());
        if(target == null) throw new MixinFormatException(name, "target doesn't exist");
        if(target.getName().equals("<init>")) throw new MixinFormatException(name, "invoking a constructor is not allowed. use @New");
        if((target.getAccess() & ACC_STATIC) != (mixinMethodEditor.getAccess() & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((target.getAccess() & ACC_STATIC) != 0 ? "" : "not") + " static");
        Type returnType = Type.getReturnType(mixinMethodEditor.getDesc());
        if(!returnType.equals(Type.getReturnType(target.getDesc())) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + Type.getReturnType(target.getDesc()) + ", " + Type.getType(Object.class));
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodEditor.getDesc());
        Type[] targetArgumentTypes = Type.getArgumentTypes(target.getDesc());
        if(mixinArgumentTypes.length != targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + i + " are:" + targetArgumentTypes[i] + ", " +Type.getType(Object.class));
        }
        if((mixinClassEditor.getAccess() & ACC_INTERFACE) == 0) throw new MixinFormatException(name, "defining class is not an interface");
        if((target.getAccess() & ACC_STATIC) == 0 && targetClassEditor.getMethodEditors().stream().anyMatch(method -> method.getName().equals(mixinMethodEditor.getName())
                && method.getDesc().split("\\)")[0].equals(mixinMethodEditor.getDesc().split("\\)")[0]) && method != target)) throw new MixinFormatException(name, "method with this name and desc already exists in the target class.");
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Invoker mixinAnnotation, ClassEditor targetClassEditor) {
        validate(mixinClassEditor, mixinMethodEditor, mixinAnnotation, targetClassEditor);
        MethodEditor targetMethodEditor = targetClassEditor.getMethodEditor(mixinAnnotation.value());

        targetMethodEditor.makePublic();

        if(!targetMethodEditor.getName().equals(mixinMethodEditor.getName()) || (targetMethodEditor.getAccess() & ACC_STATIC) != 0) {

            boolean isStatic = (targetMethodEditor.getAccess() & ACC_STATIC) != 0;

            MethodEditor editor = mixinMethodEditor;
            if(!isStatic) {
                editor = new MethodEditor(new MethodNode(mixinMethodEditor.getAccess() & ~ACC_ABSTRACT, mixinMethodEditor.getName(), mixinMethodEditor.getDesc(), mixinMethodEditor.getSignature(), mixinMethodEditor.getExceptions().toArray(String[]::new)));
                targetClassEditor.addMethod(editor);
            } else {
                BytecodeEditor bytecodeEditor = editor.getBytecodeEditor();
                for (int i = 0; i < bytecodeEditor.getBytecode().size(); i++) {
                    bytecodeEditor.remove(i);
                }
            }

            int invokeOpcode = INVOKEVIRTUAL;
            if(isStatic) invokeOpcode = INVOKESTATIC;
            else if(editor.getName().equals("<init>")) invokeOpcode = INVOKESPECIAL;
            else if((targetClassEditor.getAccess() & ACC_INTERFACE) != 0) invokeOpcode = INVOKEINTERFACE;

            int returnOpcode = TransformerHelper.getReturnOpcode(Type.getReturnType(editor.getDesc()));

            List<AbstractInsnNode> insnNodes = new ArrayList<>();

            addLoadOpcodesOfMethod(insnNodes, Type.getArgumentTypes(editor.getDesc()), isStatic);

            insnNodes.add(new MethodInsnNode(invokeOpcode, targetClassEditor.getName(), targetMethodEditor.getName(), targetMethodEditor.getDesc()));
            insnNodes.add(new InsnNode(returnOpcode));
            editor.getBytecodeEditor().add(0, insnNodes);
        }
    }
}
