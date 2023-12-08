package io.test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class UdpReceiver {

    public static void main(String[] args) throws Exception {
        InetSocketAddress address = new InetSocketAddress("192.168.10.7", 53);


        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (DatagramSocket socket = new DatagramSocket(address)) {
            socket.setSoTimeout(5000);

            byte[] bytes = new byte[65536];
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
            while (true) {
                try {
                    socket.receive(packet);
                    byte type = bytes[4];
                    if (type == 1) {
                        out.write(packet.getData(), packet.getOffset() + 5, packet.getLength() - 5);
                        int rid = bytes[0] & 0xFF;
                        rid |= (bytes[1] << 8);
                        rid |= (bytes[2] << 16);
                        rid |= (bytes[3] << 24);
                        System.out.println("收到：" + rid);
                    } else if (type == 2) {
                        break;
                    }
                    bytes[4] = (byte) -type;
                    DatagramPacket resp = new DatagramPacket(bytes, 5);
                    resp.setSocketAddress(packet.getSocketAddress());
                    socket.send(resp);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            SecretKeySpec keySpec = new SecretKeySpec("qwertyui87654321!@#$%^&*".getBytes(StandardCharsets.UTF_8),
                    "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] data = cipher.doFinal(out.toByteArray());
            Files.write(new File("vv.zip").toPath(), data);
        }

    }
}
