package com.hiresemble.document.application;

import java.util.UUID;

public interface ObjectDeletionAlertPort {
    void deadLetter(UUID outboxId, UUID userId);
}
