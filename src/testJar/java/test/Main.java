package test;

public class Main {

    public static void main(String[] args) {
        System.out.println(); //Hello, World!\n

        TestAccessor test = (TestAccessor) (Object) new Test();
        Test.Inner.DoubleInner doubleInner = new Test().new Inner().new DoubleInner();
        doubleInner.printA();

        System.out.println(test.getChanged());
        test.setChanged(Boolean.TRUE);
        System.out.println(test.getChanged());

        System.out.print(1); //1
        ((PrintStreamAccessor) System.out).newLine(); //\n
        System.out.println(new Test().getNumber()); //2\n

        ((AbstractClassTest) (Object) new AbstractClassTestSubClassMixin()).print();
    }
}
