package com.embasic.publicapp;

/**
 * Single source of truth for Explorer-like thumbnail zoom and responsive grid sizing.
 * Pure arithmetic: usable on the Swing EDT without rasterizing or reading a UDF.
 */
public final class ThumbnailSizing {
    public static final int MIN_WIDTH = 132;
    public static final int MAX_WIDTH = 272;
    public static final int DEFAULT_WIDTH = 188;
    public static final int STEP = 14;
    public static final int GAP = 12;
    private ThumbnailSizing() {}

    public record Size(int cardWidth, int cardHeight, int imageWidth, int imageHeight) {}
    public static int clamp(int width) {
        return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
    }
    public static Size at(int requestedWidth) {
        int width = clamp(requestedWidth);
        int height = (int) Math.round(width * (238d / 188d));
        return new Size(width, height, Math.max(70, width - 28), Math.max(85, height - 48));
    }
    public static int columns(int viewportWidth, int requestedCardWidth) {
        int width = at(requestedCardWidth).cardWidth();
        int usable = Math.max(1, viewportWidth - 24);
        return Math.max(1, (usable + GAP) / (width + GAP));
    }
    public static int zoomBy(int current, int steps) {
        long requested = (long) current + (long) steps * STEP;
        return clamp((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, requested)));
    }
}
