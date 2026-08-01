package com.hiresemble.ai.port;

import java.time.Duration;
import java.util.List;

/** Dedicated multimodal capability; text-only ChatGateway remains unchanged. */
public interface ImageTextExtractionGateway {

    boolean available();

    AiGatewayResponse extract(ImageTextExtractionRequest request);

    record ImageTextExtractionRequest(
            String providerKey,
            String productKey,
            String promptVersion,
            String instructions,
            List<ImageMedia> images,
            String outputSchemaVersion,
            Duration timeout,
            Long priceVersion,
            int maxOutputTokens,
            Class<?> outputType) {
        public ImageTextExtractionRequest {
            images = images == null ? List.of() : List.copyOf(images);
        }
    }

    record ImageMedia(String imageRef, String mimeType, byte[] bytes, String contentHash) {
        public ImageMedia {
            bytes = bytes == null ? null : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes == null ? null : bytes.clone();
        }
    }
}
