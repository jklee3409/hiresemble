package com.hiresemble.coverletter.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public final class TipTapContent {

    private TipTapContent() {}

    @Schema(name = "TipTapMarkDto")
    public record TipTapMarkDto(String type) {}

    @Schema(name = "TipTapNodeDto")
    public record TipTapNodeDto(
            String type,
            @Schema(nullable = true, maxLength = 20000) String text,
            List<TipTapMarkDto> marks,
            List<TipTapNodeDto> content) {
        public TipTapNodeDto {
            marks = marks == null ? List.of() : List.copyOf(marks);
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    @Schema(name = "TipTapDocumentDto")
    public record TipTapDocumentDto(String type, List<TipTapNodeDto> content) {
        public TipTapDocumentDto {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }
}
