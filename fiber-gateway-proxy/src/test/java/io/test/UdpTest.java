package io.test;


import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class UdpTest {

    private static Sender sender;

    public static void main(String[] args) throws Exception {
        sender = new Sender();
        new Thread(sender).start();

        SecretKeySpec keySpec = new SecretKeySpec("qwertyui87654321!@#$%^&*".getBytes(StandardCharsets.UTF_8),
                "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);

        File file = new File("tt.zip");
        byte[] allBytes = Files.readAllBytes(file.toPath());
        cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        allBytes = cipher.doFinal(allBytes);

        int s = 0;
        int e = allBytes.length;

        while (s < e) {
            int len = Integer.min(e - s, 1024);
            sender.send((byte) 1, allBytes, s, len);
            System.out.println("发送:" + s);
            s += len;
        }
        sender.send((byte) 2, allBytes, 0, 3);
        sender.stop = true;
    }

    static class Sender implements Runnable {
        final DatagramSocket socket = new DatagramSocket();
        private volatile int id;
        private volatile byte type;
        private volatile boolean receiving;
        private volatile boolean stop;

        Sender() throws SocketException {
            socket.setSoTimeout(5000);
        }

        void send(byte type, byte[] bytes, int offset, int length) throws Exception {
            if (length >= 65520 || type == 0) {
                throw new IllegalArgumentException();
            }
            this.type = type;
            byte[] data = new byte[length + 5];
            int id = this.id;
            data[0] = (byte) (id & 0xFF);
            data[1] = (byte) ((id >>> 8) & 0xFF);
            data[2] = (byte) ((id >>> 16) & 0xFF);
            data[3] = (byte) ((id >>> 24) & 0xFF);
            data[4] = type;
            System.arraycopy(bytes, offset, data, 5, length);
            receiving = true;

            while (receiving) {
                DatagramPacket p = new DatagramPacket(data, 0, data.length);
                InetSocketAddress address = new InetSocketAddress("192.168.10.7", 53);
                p.setSocketAddress(address);
                socket.send(p);
                System.out.println("send id:" + id);
                synchronized (this) {
                    wait(5000);
                }
            }
        }

        public void run() {
            while (!stop) {
                try {
                    byte[] rv = new byte[65536];
                    DatagramPacket datagramPacket = new DatagramPacket(rv, rv.length);
                    socket.receive(datagramPacket);
                    int rid = rv[0] & 0xFF;
                    rid |= (rv[1] << 8);
                    rid |= (rv[2] << 16);
                    rid |= (rv[3] << 24);
                    if (rid == id && type == -rv[4]) {
                        id++;
                        receiving = false;
                        synchronized (this) {
                            notify();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
