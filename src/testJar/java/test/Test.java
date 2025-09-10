package test;

public final class Test {

    private final Boolean changed = Boolean.FALSE;

    private static int getNumberInternal() {
        return 1;
    }

    public int getNumber() {
        return getNumberInternal();
    }

    public class Inner {

        public class DoubleInner {

            private final Boolean doubleInner = Boolean.TRUE;

            public void printA() {
                System.out.println("A");
                var a = changed;
            }
        }
    }
}
