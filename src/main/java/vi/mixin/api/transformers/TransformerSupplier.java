package vi.mixin.api.transformers;

import org.objectweb.asm.Opcodes;

import java.util.List;

public interface TransformerSupplier extends Opcodes {
    List<BuiltTransformer> getBuiltTransformers();
}
