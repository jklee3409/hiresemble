package com.hiresemble.job.application.port;

import java.net.URI;
import java.time.Duration;

/** Fetches one already-ranked public job-page image through the shared SSRF boundary. */
public interface JobImageFetchGateway {

    ImageAsset fetch(ImageCandidate candidate, Duration remainingDeadline);

    record ImageCandidate(String imageRef, URI uri, int score) {}

    record ImageAsset(
            String imageRef,
            String mimeType,
            byte[] bytes,
            int width,
            int height,
            String contentHash) {
        public ImageAsset {
            bytes = bytes == null ? null : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes == null ? null : bytes.clone();
        }
    }
}
