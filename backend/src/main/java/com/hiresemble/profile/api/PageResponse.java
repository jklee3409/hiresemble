package com.hiresemble.profile.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "PageResponse")
public record PageResponse<T>(
        List<T> items, int page, int size, long totalElements, int totalPages) {
    public PageResponse {
        items = List.copyOf(items);
    }
}
