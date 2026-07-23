package com.hiresemble.document.application.port;

import java.util.UUID;

public interface ObjectDeletionAlertPort {
    void deadLetter(UUID outboxId, UUID userId);
}
