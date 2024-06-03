package io.fiber.net.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class StringUtils {
    private static final int INDEX_NOT_FOUND = -1;

    public static boolean isNotEmpty(CharSequence s) {
        return s != null && s.length() > 0;
    }

    public static String[] translateCommandline(String toProcess) {
        if (toProcess == null || toProcess.length() == 0) {
            //no command? no string
            return Constant.EMPTY_STR_ARR;
        }

        // parse with a simple finite state machine

        final int normal = 0;
        final int inQuote = 1;
        final int inDoubleQuote = 2;
        boolean escape = false;
        int state = normal;
        StringTokenizer tok = new StringTokenizer(toProcess, "\\\"\' ", true);
        List<String> v = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean lastTokenHasBeenQuoted = false;

        while (tok.hasMoreTokens()) {
            String nextTok = tok.nextToken();
            if (escape) {
                if (!nextTok.equals("\"") && !nextTok.equals("'") && !nextTok.equals("\\") && !nextTok.equals(" ")) {
                    current.append('\\');
                }
                current.append(nextTok);
                escape = false;
                continue;
            } else if (nextTok.equals("\\")) {
                escape = true;
                continue;
            }
            switch (state) {
                case inQuote:
                    if ("\'".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                case inDoubleQuote:
                    if ("\"".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                default:
                    if ("\'".equals(nextTok)) {
                        state = inQuote;
                    } else if ("\"".equals(nextTok)) {
                        state = inDoubleQuote;
                    } else if (" ".equals(nextTok)) {
                        if (lastTokenHasBeenQuoted || current.length() != 0) {
                            v.add(current.toString());
                            current = new StringBuilder();
                        }
                    } else {
                        current.append(nextTok);
                    }
                    lastTokenHasBeenQuoted = false;
                    break;
            }
        }

        if (lastTokenHasBeenQuoted || current.length() != 0) {
            v.add(current.toString());
        }

        if (state == inQuote || state == inDoubleQuote) {
            throw new IllegalArgumentException("unbalanced quotes in " + toProcess);
        }

        return v.toArray(Constant.EMPTY_STR_ARR);
    }

    public static boolean isEmpty(Object str) {
        return (str == null || "".equals(str));
    }

    public static boolean isEmpty(CharSequence str) {
        return (str == null || "".contentEquals(str));
    }

    public static boolean isEmpty(String str) {
        return (str == null || str.isEmpty());
    }

    public static boolean hasLength(CharSequence str) {
        return (str != null && str.length() > 0);
    }

    public static boolean hasLength(String str) {
        return hasLength((CharSequence) str);
    }

    public static boolean hasText(CharSequence str) {
        if (!hasLength(str)) {
            return false;
        }
        int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasText(String str) {
        return hasText((CharSequence) str);
    }

    public static String camelToKebabCase(String camel) {
        if (StringUtils.isEmpty(camel)) {
            return camel;
        }
        StringBuilder sb = new StringBuilder(camel.length() + 4);
        boolean f = true;
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                if (!f) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
            f = false;
        }
        return sb.toString();
    }

    public static int lastIndexOfIgnoreCase(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return INDEX_NOT_FOUND;
        }
        return lastIndexOfIgnoreCase(str, searchStr, str.length());
    }

    /**
     * <p>Case in-sensitive find of the last index within a String
     * from the specified position.</p>
     *
     * <p>A <code>null</code> String will return <code>-1</code>.
     * A negative start position returns <code>-1</code>.
     * An empty ("") search String always matches unless the start position is negative.
     * A start position greater than the string length searches the whole string.</p>
     *
     * <pre>
     * StringUtils.lastIndexOfIgnoreCase(null, *, *)          = -1
     * StringUtils.lastIndexOfIgnoreCase(*, null, *)          = -1
     * StringUtils.lastIndexOfIgnoreCase("aabaabaa", "A", 8)  = 7
     * StringUtils.lastIndexOfIgnoreCase("aabaabaa", "B", 8)  = 5
     * StringUtils.lastIndexOfIgnoreCase("aabaabaa", "AB", 8) = 4
     * StringUtils.lastIndexOfIgnoreCase("aabaabaa", "B", 9)  = 5
     * StringUtils.lastIndexOfIgnoreCase("aabaabaa", "B", -1) = -1
     * StringUtils.lastIndexOfIgnoreCase("aabaabaa", "A", 0)  = 0
     * StringUtils.lastIndexOfIgnoreCase("aabaabaa", "B", 0)  = -1
     * </pre>
     *
     * @param str       the String to check, may be null
     * @param searchStr the String to find, may be null
     * @param startPos  the start position
     * @return the first index of the search String,
     * -1 if no match or <code>null</code> string input
     */
    public static int lastIndexOfIgnoreCase(String str, String searchStr, int startPos) {
        if (str == null || searchStr == null) {
            return INDEX_NOT_FOUND;
        }
        if (startPos > (str.length() - searchStr.length())) {
            startPos = str.length() - searchStr.length();
        }
        if (startPos < 0) {
            return INDEX_NOT_FOUND;
        }
        if (searchStr.isEmpty()) {
            return startPos;
        }

        for (int i = startPos; i >= 0; i--) {
            if (str.regionMatches(true, i, searchStr, 0, searchStr.length())) {
                return i;
            }
        }
        return INDEX_NOT_FOUND;
    }

    public static String trimRight(String src, String search) {
        if (isEmpty(src) || isEmpty(search)) {
            return src;
        }
        int len = search.length();
        int e = src.length() - len;
        while (e >= 0 && src.regionMatches(e, search, 0, len)) {
            e -= len;
        }
        return src.substring(0, e + len);
    }

    public static String trimLeft(String src, String search) {
        if (isEmpty(src) || isEmpty(search)) {
            return src;
        }
        int len = search.length();
        int oLen = src.length();
        int s = 0;
        while (s < oLen && src.regionMatches(s, search, 0, len)) {
            s += len;
        }
        return src.substring(s);
    }

    public static String trim(String src, String search) {
        if (isEmpty(src) || isEmpty(search)) {
            return src;
        }
        int len = search.length();
        int oLen = src.length();
        int s = 0, e = oLen - len;
        while (s < oLen && src.regionMatches(s, search, 0, len)) {
            s += len;
        }

        while (e >= s && src.regionMatches(e, search, 0, len)) {
            e -= len;
        }
        e += len;
        if (s >= e) {
            return "";
        }
        return src.substring(s, e);
    }

    public static String trimLeftEmpty(String src) {
        if (isEmpty(src)) {
            return src;
        }

        int i = 0;
        while (i < src.length() && Character.isWhitespace(src.charAt(i))) {
            i++;
        }
        return i >= src.length() ? "" : src.substring(i);
    }

    public static String trimRightEmpty(String src) {
        if (isEmpty(src)) {
            return src;
        }
        int i = src.length() - 1;
        while (i >= 0 && Character.isWhitespace(src.charAt(i))) {
            i--;
        }
        return i < 0 ? "" : src.substring(0, i + 1);
    }

    /**
     * Performs the logic for the {@code split} and
     * {@code splitPreserveAllTokens} methods that return a maximum array
     * length.
     *
     * @param str               the String to parse, may be {@code null}
     * @param separatorChars    the separate character
     * @param max               the maximum number of elements to include in the
     *                          array. A zero or negative value implies no limit.
     * @param preserveAllTokens if {@code true}, adjacent separators are
     *                          treated as empty token separators; if {@code false}, adjacent
     *                          separators are treated as one separator.
     * @return an array of parsed Strings, {@code null} if null String input
     */
    private static String[] splitWorker(final String str, final String separatorChars, final int max, final boolean preserveAllTokens) {
        // Performance tuned for 2.0 (JDK1.4)
        // Direct code is quicker than StringTokenizer.
        // Also, StringTokenizer uses isSpace() not isWhitespace()

        if (str == null) {
            return null;
        }
        final int len = str.length();
        if (len == 0) {
            return Constant.EMPTY_STR_ARR;
        }
        final List<String> list = new ArrayList<String>();
        int sizePlus1 = 1;
        int i = 0, start = 0;
        boolean match = false;
        boolean lastMatch = false;
        if (separatorChars == null) {
            // Null separator means use whitespace
            while (i < len) {
                if (Character.isWhitespace(str.charAt(i))) {
                    if (match || preserveAllTokens) {
                        lastMatch = true;
                        if (sizePlus1++ == max) {
                            i = len;
                            lastMatch = false;
                        }
                        list.add(str.substring(start, i));
                        match = false;
                    }
                    start = ++i;
                    continue;
                }
                lastMatch = false;
                match = true;
                i++;
            }
        } else if (separatorChars.length() == 1) {
            // Optimise 1 character case
            final char sep = separatorChars.charAt(0);
            while (i < len) {
                if (str.charAt(i) == sep) {
                    if (match || preserveAllTokens) {
                        lastMatch = true;
                        if (sizePlus1++ == max) {
                            i = len;
                            lastMatch = false;
                        }
                        list.add(str.substring(start, i));
                        match = false;
                    }
                    start = ++i;
                    continue;
                }
                lastMatch = false;
                match = true;
                i++;
            }
        } else {
            // standard case
            while (i < len) {
                if (separatorChars.indexOf(str.charAt(i)) >= 0) {
                    if (match || preserveAllTokens) {
                        lastMatch = true;
                        if (sizePlus1++ == max) {
                            i = len;
                            lastMatch = false;
                        }
                        list.add(str.substring(start, i));
                        match = false;
                    }
                    start = ++i;
                    continue;
                }
                lastMatch = false;
                match = true;
                i++;
            }
        }
        if (match || preserveAllTokens && lastMatch) {
            list.add(str.substring(start, i));
        }
        return list.toArray(Constant.EMPTY_STR_ARR);
    }

    public static String[] split(final String str, final String separatorChars) {
        return splitWorker(str, separatorChars, -1, false);
    }

    public static boolean containsAny(final String src, final char... searchChars) {
        if (isEmpty(src) || ArrayUtils.isEmpty(searchChars)) {
            return false;
        }
        char[] cs = CharArrUtil.toCharArr(src);
        final int csLength = cs.length;
        final int searchLength = searchChars.length;
        final int csLast = csLength - 1;
        final int searchLast = searchLength - 1;
        for (int i = 0; i < csLength; i++) {
            final char ch = cs[i];
            for (int j = 0; j < searchLength; j++) {
                if (searchChars[j] == ch) {
                    if (Character.isHighSurrogate(ch)) {
                        if (j == searchLast) {
                            // missing low surrogate, fine, like String.indexOf(String)
                            return true;
                        }
                        if (i < csLast && searchChars[j + 1] == cs[i + 1]) {
                            return true;
                        }
                    } else {
                        // ch is in the Basic Multilingual Plane
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static int indexOfAny(final String src, final char... searchChars) {
        if (isEmpty(src) || ArrayUtils.isEmpty(searchChars)) {
            return INDEX_NOT_FOUND;
        }
        char[] cs = CharArrUtil.toCharArr(src);
        final int csLen = cs.length;
        final int csLast = csLen - 1;
        final int searchLen = searchChars.length;
        final int searchLast = searchLen - 1;
        for (int i = 0; i < csLen; i++) {
            final char ch = cs[i];
            for (int j = 0; j < searchLen; j++) {
                if (searchChars[j] == ch) {
                    if (i < csLast && j < searchLast && Character.isHighSurrogate(ch)) {
                        // ch is a supplementary character
                        if (searchChars[j + 1] == cs[i + 1]) {
                            return i;
                        }
                    } else {
                        return i;
                    }
                }
            }
        }
        return INDEX_NOT_FOUND;
    }
}
