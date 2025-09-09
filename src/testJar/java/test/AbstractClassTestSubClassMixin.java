package test;

import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.classes.Extends;

@Mixin(AbstractClassTest.class) @Extends
public class AbstractClassTestSubClassMixin {

    public void print() {
        System.out.println("overridden");
    }
}
