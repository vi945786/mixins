package vi.mixin.api.util;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.*;

public class TypeAwareBasicInterpreter extends BasicInterpreter {

    public TypeAwareBasicInterpreter() {
        super(ASM9);
    }

    public TypeAwareBasicInterpreter(int api) {
        super(api);
    }

    @Override
    public BasicValue newValue(final Type type) {
        return new BasicValue(type);
    }
}
