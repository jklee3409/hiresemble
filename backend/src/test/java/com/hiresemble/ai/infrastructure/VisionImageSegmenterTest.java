package com.hiresemble.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class VisionImageSegmenterTest {

    @Test
    void tallPostingImageIsSplitIntoOverlappingSegmentsThatCoverEveryRow() throws IOException {
        BufferedImage source = striped(1000, 4148);

        List<VisionImageSegmenter.Segment> segments =
                VisionImageSegmenter.segments("image/jpeg", encode(source, "jpg"));

        assertThat(segments).hasSize(3);
        int stitchedHeight = 0;
        for (VisionImageSegmenter.Segment segment : segments) {
            assertThat(segment.mimeType()).isEqualTo("image/png");
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(segment.bytes()));
            assertThat(decoded.getWidth()).isEqualTo(1000);
            assertThat(decoded.getHeight())
                    .isLessThanOrEqualTo((int) (1000 * VisionImageSegmenter.MAX_SEGMENT_ASPECT));
            stitchedHeight += decoded.getHeight() - VisionImageSegmenter.OVERLAP_PIXELS;
        }
        assertThat(stitchedHeight + VisionImageSegmenter.OVERLAP_PIXELS)
                .isGreaterThanOrEqualTo(4148);
        BufferedImage last = ImageIO.read(new ByteArrayInputStream(segments.getLast().bytes()));
        int lastRowBlue = last.getRGB(500, last.getHeight() - 1) & 0xff;
        int sourceLastRowBlue = source.getRGB(500, 4147) & 0xff;
        assertThat(Math.abs(lastRowBlue - sourceLastRowBlue)).isLessThan(24);
    }

    @Test
    void extremelyTallImageIsCappedAtTheMaximumSegmentCount() throws IOException {
        List<VisionImageSegmenter.Segment> segments = VisionImageSegmenter.segments(
                "image/png", encode(striped(400, 12_000), "png"));

        assertThat(segments).hasSize(VisionImageSegmenter.MAX_SEGMENTS);
        int total = 0;
        for (VisionImageSegmenter.Segment segment : segments) {
            total += ImageIO.read(new ByteArrayInputStream(segment.bytes())).getHeight();
        }
        assertThat(total - (segments.size() - 1) * VisionImageSegmenter.OVERLAP_PIXELS)
                .isGreaterThanOrEqualTo(12_000);
    }

    @Test
    void regularOrUndecodableImagesAreSentUnchanged() throws IOException {
        byte[] regular = encode(striped(1200, 1800), "png");
        byte[] garbage = {1, 2, 3};

        assertThat(VisionImageSegmenter.segments("image/png", regular))
                .singleElement()
                .satisfies(segment -> {
                    assertThat(segment.mimeType()).isEqualTo("image/png");
                    assertThat(segment.bytes()).isSameAs(regular);
                });
        assertThat(VisionImageSegmenter.segments("image/webp", garbage))
                .singleElement()
                .satisfies(segment -> {
                    assertThat(segment.mimeType()).isEqualTo("image/webp");
                    assertThat(segment.bytes()).isSameAs(garbage);
                });
    }

    static BufferedImage striped(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int y = 0; y < height; y += 40) {
                graphics.setColor(y / 40 % 2 == 0 ? Color.WHITE : Color.DARK_GRAY);
                graphics.fillRect(0, y, width, 40);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
