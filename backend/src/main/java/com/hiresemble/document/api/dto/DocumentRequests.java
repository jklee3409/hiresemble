package com.hiresemble.document.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public final class DocumentRequests {

    private DocumentRequests() {}

    @Schema(name = "DocumentManualTextRequest")
    public record ManualTextRequest(
            @NotNull @Schema(minLength = 100, maxLength = 500000) String text,
            @NotNull @PositiveOrZero Long version) {}

    @Schema(name = "DocumentReparseRequest")
    public record ReparseRequest(@NotNull @PositiveOrZero Long version) {}
}
