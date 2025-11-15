package io.fiber.net.example;

/**
 * Formats epoch millisecond timestamps without relying on date/time libraries.
 * The implementation avoids heap allocations beyond the result String by using
 * primitive math and direct writes into a temporary char buffer.
 */
public final class ManualTimestampFormatter {

    private static final long MILLIS_PER_SECOND = 1000L;
    private static final long MILLIS_PER_MINUTE = 60L * MILLIS_PER_SECOND;
    private static final long MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE;
    private static final long MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR;
    private static final long DAYS_PER_CYCLE = 146097L;
    private static final long DAYS_0000_TO_1970 = 719528L;

    private ManualTimestampFormatter() {
    }

    public static String formatUtc(long epochMillis) {
        return format(epochMillis, 0);
    }

    public static String format(long epochMillis, int offsetMinutes) {
        long offsetMillis = ((long) offsetMinutes) * MILLIS_PER_MINUTE;
        long adjustedMillis = epochMillis + offsetMillis;

        long epochDay = floorDiv(adjustedMillis, MILLIS_PER_DAY);
        long millisOfDay = floorMod(adjustedMillis, MILLIS_PER_DAY);

        int hour = (int) (millisOfDay / MILLIS_PER_HOUR);
        millisOfDay -= hour * MILLIS_PER_HOUR;
        int minute = (int) (millisOfDay / MILLIS_PER_MINUTE);
        millisOfDay -= minute * MILLIS_PER_MINUTE;
        int second = (int) (millisOfDay / MILLIS_PER_SECOND);
        int millis = (int) (millisOfDay - second * MILLIS_PER_SECOND);

        long packedDate = toPackedDate(epochDay);
        int year = (int) (packedDate >> 32);
        int month = (int) ((packedDate >> 16) & 0xFFFF);
        int day = (int) (packedDate & 0xFFFF);

        char[] buffer = new char[32];
        int pos = writeYear(buffer, 0, year);
        buffer[pos++] = '-';
        pos = writeTwoDigits(buffer, pos, month);
        buffer[pos++] = '-';
        pos = writeTwoDigits(buffer, pos, day);
        buffer[pos++] = ' ';
        pos = writeTwoDigits(buffer, pos, hour);
        buffer[pos++] = ':';
        pos = writeTwoDigits(buffer, pos, minute);
        buffer[pos++] = ':';
        pos = writeTwoDigits(buffer, pos, second);
        buffer[pos++] = '.';
        pos = writeThreeDigits(buffer, pos, millis);

        return new String(buffer, 0, pos);
    }

    private static long toPackedDate(long epochDay) {
        long zeroDay = epochDay + DAYS_0000_TO_1970 - 60;
        long adjust = 0;
        if (zeroDay < 0) {
            long adjustCycles = (zeroDay + 1) / DAYS_PER_CYCLE - 1;
            zeroDay -= adjustCycles * DAYS_PER_CYCLE;
            adjust = adjustCycles * 400;
        }
        long yearEst = (400 * zeroDay + 591) / DAYS_PER_CYCLE;
        long doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        if (doyEst < 0) {
            yearEst--;
            doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        }
        yearEst += adjust;
        int marchDoy0 = (int) doyEst;
        int marchMonth0 = (marchDoy0 * 5 + 2) / 153;
        int month = (marchMonth0 + 2) % 12 + 1;
        int day = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1;
        int year = (int) (yearEst + marchMonth0 / 10);
        return ((long) year << 32) | ((long) month << 16) | day;
    }

    private static long floorDiv(long x, long y) {
        long r = x / y;
        long m = x % y;
        if ((m != 0) && ((x ^ y) < 0)) {
            r--;
        }
        return r;
    }

    private static long floorMod(long x, long y) {
        long m = x % y;
        if ((m != 0) && ((x ^ y) < 0)) {
            m += y;
        }
        return m;
    }

    private static int writeYear(char[] buffer, int pos, int year) {
        if (year >= 0 && year <= 9999) {
            return writeFourDigits(buffer, pos, year);
        }
        if (year < 0 && year >= -9999) {
            buffer[pos++] = '-';
            return writeFourDigits(buffer, pos, -year);
        }
        if (year > 9999) {
            buffer[pos++] = '+';
            return writeNumber(buffer, pos, year);
        }
        buffer[pos++] = '-';
        return writeNumber(buffer, pos, -(long) year);
    }

    private static int writeFourDigits(char[] buffer, int pos, int value) {
        buffer[pos++] = (char) ('0' + value / 1000);
        value %= 1000;
        buffer[pos++] = (char) ('0' + value / 100);
        value %= 100;
        buffer[pos++] = (char) ('0' + value / 10);
        buffer[pos++] = (char) ('0' + value % 10);
        return pos;
    }

    private static int writeTwoDigits(char[] buffer, int pos, int value) {
        buffer[pos++] = (char) ('0' + value / 10);
        buffer[pos++] = (char) ('0' + value % 10);
        return pos;
    }

    private static int writeThreeDigits(char[] buffer, int pos, int value) {
        buffer[pos++] = (char) ('0' + value / 100);
        value %= 100;
        buffer[pos++] = (char) ('0' + value / 10);
        buffer[pos++] = (char) ('0' + value % 10);
        return pos;
    }

    private static int writeNumber(char[] buffer, int pos, long value) {
        long number = value;
        if (number < 0) {
            number = -number;
        }
        int start = pos;
        do {
            long quotient = number / 10;
            int digit = (int) (number - quotient * 10);
            buffer[pos++] = (char) ('0' + digit);
            number = quotient;
        } while (number > 0);
        reverse(buffer, start, pos - 1);
        return pos;
    }

    private static void reverse(char[] buffer, int start, int end) {
        while (start < end) {
            char tmp = buffer[start];
            buffer[start] = buffer[end];
            buffer[end] = tmp;
            start++;
            end--;
        }
    }
}
