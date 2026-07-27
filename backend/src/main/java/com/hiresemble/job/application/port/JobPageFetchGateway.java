package com.hiresemble.job.application.port;

import java.net.URI;

public interface JobPageFetchGateway {

    FetchResult fetch(URI uri);

    record FetchResult(
            URI finalUri,
            PageClassification classification,
            String html,
            int httpStatus) {
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

    enum PageClassification {
        FETCHED,
        LOGIN_REQUIRED,
        BOT_BLOCKED,
        JAVASCRIPT_REQUIRED,
        EMPTY
    }
}
