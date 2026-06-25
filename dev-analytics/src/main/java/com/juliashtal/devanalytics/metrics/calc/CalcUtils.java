package com.juliashtal.devanalytics.metrics.calc;

import java.util.List;

/** Package-private static utilities shared by metric calculators. */
final class CalcUtils {

    private CalcUtils() {}

    static double medianOfLongs(List<Long> sortedValues) {
        int n = sortedValues.size();
        if (n == 0) return 0.0;
        if (n % 2 == 1) return sortedValues.get(n / 2);
        return (sortedValues.get(n / 2 - 1) + sortedValues.get(n / 2)) / 2.0;
    }
}
