package com.hiresemble.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "AccountDeletionAcceptedDto")
public record AccountDeletionAcceptedDto(UUID deletionRequestId, Instant purgeBy) {}
