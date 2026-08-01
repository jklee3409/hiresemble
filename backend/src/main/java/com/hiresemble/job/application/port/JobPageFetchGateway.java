package com.hiresemble.job.application.port;

import java.net.URI;

public interface JobPageFetchGateway {

    FetchResult fetch(URI uri);

    record FetchResult(
            URI finalUri,
            PageClassification classification,
            String html,
            int httpStatus,
            CharsetMetadata charsetMetadata) {
        public FetchResult(
                URI finalUri, PageClassification classification, String html, int httpStatus) {
            this(finalUri, classification, html, httpStatus, null);
        }

        public FetchResult {
            if (finalUri == null || classification == null || httpStatus < 100 || httpStatus > 599) {
                throw new IllegalArgumentException("job page fetch result is invalid");
            }
            if (classification == PageClassification.FETCHED
                    && (html == null || html.isBlank())) {
                throw new IllegalArgumentException("fetched job page must contain HTML");
            }
            if (classification != PageClassification.FETCHED) {
                html = null;
            }
        }
    }

    record CharsetMetadata(
            String declaredCharset,
            String resolvedCharset,
            CharsetDetectionSource detectionSource,
            int rawByteLength,
            int decodedCharacterLength,
            int replacementCharacterCount,
            double replacementCharacterRatio,
            String contentHash) {}

    enum CharsetDetectionSource {
        HEADER,
        BOM,
        META,
        DEFAULT,
        FALLBACK
    }

    enum PageClassification {
        FETCHED,
        LOGIN_REQUIRED,
        BOT_BLOCKED,
        JAVASCRIPT_REQUIRED,
        EMPTY
    }
}
