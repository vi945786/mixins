package test;

public final class Test {

    private final Boolean changed = Boolean.FALSE;

    private static int getNumberInternal() {
        return 1;
    }

    public int getNumber() {
        return getNumberInternal();
    }
}
