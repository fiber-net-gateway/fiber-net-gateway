package lua.test;

public class Et {
    public static void main(String[] args) {
        try {

            try {
                int a = 1 / 0;
            } catch (Throwable e) {
                System.out.println("===");
                int a = 1 / 0;
            }
        } catch (Throwable e) {
            System.out.println("+++");
        }

    }
}
