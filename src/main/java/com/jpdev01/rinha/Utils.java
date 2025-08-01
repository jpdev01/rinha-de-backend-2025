package com.jpdev01.rinha;

public class Utils {

    public static boolean isDelayed(long start, long end) {
        long durationMs = (end - start) / 1_000_000;
        return durationMs > 10;
    }
}
