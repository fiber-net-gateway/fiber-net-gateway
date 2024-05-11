package io.fiber.net.common.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {
    private static final char[] HEX_ARR = "0123456789abcdef".toCharArray();

    public static byte[] stringToMD5(String plainText) {
        try {
            return MessageDigest.getInstance("md5").digest(
                    plainText.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("没有这个md5算法！");
        }
    }

    public static String stringToMD5Str(String plainText) {
        byte[] bytes = stringToMD5(plainText);
        return hex(bytes);
    }

    public static byte[] bytesToMD5(byte[] bytes) {
        try {
            return MessageDigest.getInstance("md5").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("没有这个md5算法！");
        }
    }

    public static String bytesToMD5Str(byte[] bytes) {
        try {
            return hex(MessageDigest.getInstance("md5").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("没有这个md5算法！");
        }
    }

    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_ARR[(((int) b) & 0xF0) >> 4]);
            sb.append(HEX_ARR[((int) b & 0xF)]);
        }
        return sb.toString();
    }

    public static byte[] sha256(byte[] arr) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("SHA-256");
            return md5.digest(arr);
        } catch (Exception e) {
            // not hit
            throw new RuntimeException(e);
        }
    }

    public static byte[] sha1(byte[] arr) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("SHA-1");
            return md5.digest(arr);
        } catch (Exception e) {
            // not hit
            throw new RuntimeException(e);
        }
    }
}