package com.hiresemble.ai.infrastructure;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Splits tall recruitment images into overlapping top-to-bottom segments before a vision call.
 *
 * <p>OpenAI vision first fits an image into 2048x2048 and then shrinks its short side to 768px. A
 * single 1000x4000 posting therefore arrives at about 500px wide and small Korean text becomes
 * unreadable. Segments whose height is at most twice their width keep the same scale as a square
 * image, so a text-only posting image stays legible. Images that cannot be decoded, or that are not
 * tall, are sent unchanged.
 */
final class VisionImageSegmenter {

    static final int MAX_SEGMENTS = 8;
    static final double MAX_SEGMENT_ASPECT = 2.0d;
    static final int OVERLAP_PIXELS = 80;
    private static final long MAX_DECODED_PIXELS = 40_000_000L;
    private static final long MAX_SEGMENT_BYTES_TOTAL = 20L * 1024 * 1024;

    private VisionImageSegmenter() {}

    static List<Segment> segments(String mimeType, byte[] bytes) {
        List<Segment> original = List.of(new Segment(mimeType, bytes));
        BufferedImage image = decode(bytes);
        if (image == null) {
            return original;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int segmentHeight = (int) Math.floor(width * MAX_SEGMENT_ASPECT);
        if (width <= 0 || height <= segmentHeight || segmentHeight <= OVERLAP_PIXELS) {
            return original;
        }
        int count = (int) Math.ceil(
                (double) (height - OVERLAP_PIXELS) / (segmentHeight - OVERLAP_PIXELS));
        if (count > MAX_SEGMENTS) {
            count = MAX_SEGMENTS;
            segmentHeight = (int) Math.ceil(
                    (double) (height - OVERLAP_PIXELS) / count) + OVERLAP_PIXELS;
        }
        int step = segmentHeight - OVERLAP_PIXELS;
        List<Segment> segments = new ArrayList<>(count);
        long totalBytes = 0;
        for (int index = 0; index < count; index++) {
            int top = Math.min(index * step, Math.max(0, height - segmentHeight));
            int bottom = index == count - 1 ? height : Math.min(height, top + segmentHeight);
            byte[] encoded = png(image.getSubimage(0, top, width, bottom - top));
            if (encoded == null) {
                return original;
            }
            totalBytes += encoded.length;
            if (totalBytes > MAX_SEGMENT_BYTES_TOTAL) {
                return original;
            }
            segments.add(new Segment("image/png", encoded));
        }
        return List.copyOf(segments);
    }

    private static BufferedImage decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null
                    || (long) image.getWidth() * image.getHeight() > MAX_DECODED_PIXELS) {
                return null;
            }
            return image;
        } catch (IOException | RuntimeException undecodable) {
            return null;
        }
    }

    private static byte[] png(BufferedImage segment) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            return ImageIO.write(segment, "png", output) ? output.toByteArray() : null;
        } catch (IOException | RuntimeException unencodable) {
            return null;
        }
    }

    record Segment(String mimeType, byte[] bytes) {}
}
