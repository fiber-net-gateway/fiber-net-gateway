package io.fiber.net.test;

public class TT {
    public static void main(String[] args) {

        for (int i = 0; i < 256; i++) {
            byte b = (byte) i;
            if ((b & 0xFF) != i) {
                System.out.println(i);
            }
        }
    }
}
