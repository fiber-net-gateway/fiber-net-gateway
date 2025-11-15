package io.fiber.net.example;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public final class ManualTimestampFormatterBenchmark {

    private static final int SAMPLE_SIZE = 100000;
    private static final int RUNS = 5;
    private static final int MAX_OFFSET_MINUTES = 1080;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS");

    private ManualTimestampFormatterBenchmark() {
    }

    public static void main(String[] args) {
        long[] timestamps = new long[SAMPLE_SIZE];
        int[] offsets = new int[SAMPLE_SIZE];
        Random random = new Random(0xBEEFL);
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            timestamps[i] = random.nextLong();
            offsets[i] = random.nextInt(MAX_OFFSET_MINUTES * 2 + 1) - MAX_OFFSET_MINUTES;
        }

        warmup(timestamps, offsets);

        long manualNanos = measureManual(timestamps, offsets);
        long standardNanos = measureStandard(timestamps, offsets);

        System.out.printf("Manual formatter:   %.2f ms%n", manualNanos / 1_000_000.0);
        System.out.printf("Standard formatter: %.2f ms%n", standardNanos / 1_000_000.0);
        System.out.println(ManualTimestampFormatter.format(System.currentTimeMillis(),
                ZonedDateTime.now(ZoneId.systemDefault()).getOffset().getTotalSeconds() / 60));
    }

    private static void warmup(long[] timestamps, int[] offsets) {
        measureManual(timestamps, offsets);
        measureStandard(timestamps, offsets);
    }

    private static long measureManual(long[] timestamps, int[] offsets) {
        long checksum = 0;
        long start = System.nanoTime();
        for (int run = 0; run < RUNS; run++) {
            for (int i = 0; i < timestamps.length; i++) {
                checksum += ManualTimestampFormatter.format(timestamps[i], offsets[i]).hashCode();
            }
        }
        long elapsed = System.nanoTime() - start;
        if (checksum == 0) {
            System.out.println("Ignore checksum: " + checksum);
        }
        return elapsed;
    }

    private static long measureStandard(long[] timestamps, int[] offsets) {
        long checksum = 0;
        long start = System.nanoTime();
        for (int run = 0; run < RUNS; run++) {
            for (int i = 0; i < timestamps.length; i++) {
                checksum += formatWithStandardLibrary(timestamps[i], offsets[i]).hashCode();
            }
        }
        long elapsed = System.nanoTime() - start;
        if (checksum == 0) {
            System.out.println("Ignore checksum: " + checksum);
        }
        return elapsed;
    }

    private static String formatWithStandardLibrary(long epochMillis, int offsetMinutes) {
        ZoneOffset offset = ZoneOffset.ofTotalSeconds(offsetMinutes * 60);
        return FORMATTER.format(Instant.ofEpochMilli(epochMillis).atOffset(offset));
    }
}
