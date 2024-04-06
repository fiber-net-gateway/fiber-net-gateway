package lua.test;

public class ShowByteCode {
    public static void t() {

        try {
            System.out.println(111);
            try {
                int i = 2;
                long p = System.currentTimeMillis() / i;
                System.out.println(p);
            } catch (RuntimeException e) {
                System.out.println(222);
            }
        } catch (RuntimeException e) {
            System.out.println(333);
        }
    }

    public static void main(String[] args) {
        t();
    }
}
