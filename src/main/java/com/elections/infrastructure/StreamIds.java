package com.elections.infrastructure;

/** Comparación de IDs de Redis Stream ("<ms>-<seq>"). */
public final class StreamIds {

    private StreamIds() {}

    public static boolean isValid(String id) {
        return id != null && id.matches("\\d+(-\\d+)?");
    }

    public static int compare(String a, String b) {
        long[] x = parse(a);
        long[] y = parse(b);
        int c = Long.compare(x[0], y[0]);
        return c != 0 ? c : Long.compare(x[1], y[1]);
    }

    private static long[] parse(String id) {
        int dash = id.indexOf('-');
        if (dash < 0) return new long[]{Long.parseLong(id), 0};
        return new long[]{Long.parseLong(id.substring(0, dash)), Long.parseLong(id.substring(dash + 1))};
    }
}
