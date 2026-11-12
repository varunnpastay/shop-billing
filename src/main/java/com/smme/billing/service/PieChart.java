package com.smme.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A donut chart drawn as SVG on the server (no chart library). The ring is a circle whose circumference is 100 units,
 * so each slice's dash length is simply its percentage. Colours are taken in a fixed order and never cycled;
 * anything past the sixth slice is folded into "Other".
 */
public record PieChart(String title, String subtitle, BigDecimal total, List<Slice> slices) {

    /** Colour-blind-safe categorical order (validated palette, light mode). */
    public static final List<String> COLORS = List.of("#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#6250d6");
    private static final double GAP = 0.6;   // surface gap between slices, in ring units (about 2px at 170px)

    /** One slice: dash = visible arc length, offset = where it starts on the ring. */
    public record Slice(String label, BigDecimal value, int percent, String color, double dash, double offset) {}

    public boolean isEmpty() { return slices.isEmpty(); }

    public static PieChart of(String title, String subtitle, Map<String, BigDecimal> data) {
        List<Map.Entry<String, BigDecimal>> rows = data.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().signum() > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .toList();
        // fold the tail into "Other" so no colour is ever reused
        List<Map.Entry<String, BigDecimal>> kept = new ArrayList<>(rows.stream().limit(COLORS.size()).toList());
        if (rows.size() > COLORS.size()) {
            kept = new ArrayList<>(rows.subList(0, COLORS.size() - 1));
            BigDecimal other = rows.subList(COLORS.size() - 1, rows.size()).stream()
                    .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            kept.add(Map.entry("Other", other));
        }
        BigDecimal total = kept.stream().map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Slice> slices = new ArrayList<>();
        double start = 0;
        for (int i = 0; i < kept.size(); i++) {
            BigDecimal v = kept.get(i).getValue();
            double share = v.doubleValue() * 100 / total.doubleValue();
            double dash = kept.size() == 1 ? 100 : Math.max(share - GAP, 0.2);
            // SVG dashes start at 3 o'clock and run clockwise; offset 25 moves the start to 12 o'clock
            slices.add(new Slice(kept.get(i).getKey(), v,
                    v.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue(),
                    COLORS.get(i), round(dash), round(25 - start)));
            start += share;
        }
        return new PieChart(title, subtitle, total, slices);
    }

    private static double round(double d) {
        return Math.round(d * 100) / 100.0;
    }
}
