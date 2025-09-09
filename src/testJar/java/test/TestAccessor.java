package test;

import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.methods.Getter;
import vi.mixin.api.annotations.methods.Setter;

@Mixin(Test.class)
public interface TestAccessor {

    @Getter("changed")
    Boolean getChanged();

    @Setter("changed")
    void setChanged(Boolean b);
}

