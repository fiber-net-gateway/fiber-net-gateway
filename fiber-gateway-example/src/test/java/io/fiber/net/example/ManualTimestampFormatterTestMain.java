package io.fiber.net.example;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public final class ManualTimestampFormatterTestMain {

    private static final int MAX_OFFSET_MINUTES = 1080;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS");

    private ManualTimestampFormatterTestMain() {
    }

    public static void main(String[] args) {
        Random random = new Random(0xC0FFEE);
        verifyCase(0L, 0);
        verifyCase(1683356889123L, 480);
        verifyCase(951868799999L, -300);
        verifyCase(-999L, 0);
        verifyCase(0L, -720);
        verifyCase(0L, 720);
        for (int i = 0; i < 10000; i++) {
            long epochMillis = random.nextLong();
            int offsetMinutes = random.nextInt(MAX_OFFSET_MINUTES * 2 + 1) - MAX_OFFSET_MINUTES;
            verifyCase(epochMillis, offsetMinutes);
        }
        System.out.println("ManualTimestampFormatter random tests passed.");
    }

    private static void verifyCase(long epochMillis, int offsetMinutes) {
        String expected = formatWithStandardLibrary(epochMillis, offsetMinutes);
        String actual = ManualTimestampFormatter.format(epochMillis, offsetMinutes);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Mismatch for epoch=" + epochMillis
                    + " offsetMinutes=" + offsetMinutes
                    + " expected=" + expected + " actual=" + actual);
        }
        String utc = ManualTimestampFormatter.formatUtc(epochMillis);
        if (offsetMinutes == 0 && !utc.equals(actual)) {
            throw new IllegalStateException("UTC helper mismatch for epoch=" + epochMillis);
        }
    }

    private static String formatWithStandardLibrary(long epochMillis, int offsetMinutes) {
        ZoneOffset offset = ZoneOffset.ofTotalSeconds(offsetMinutes * 60);
        return FORMATTER.format(Instant.ofEpochMilli(epochMillis).atOffset(offset));
    }
}
