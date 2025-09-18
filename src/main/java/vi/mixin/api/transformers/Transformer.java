package vi.mixin.api.transformers;

import org.objectweb.asm.Opcodes;

public sealed interface Transformer extends Opcodes permits ClassTransformer, InnerElementTransformer  {
}
