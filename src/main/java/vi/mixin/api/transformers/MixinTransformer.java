package vi.mixin.api.transformers;

import org.objectweb.asm.Opcodes;

public sealed interface MixinTransformer extends Opcodes permits ClassTransformer, FieldTransformer, MethodTransformer  {
}
