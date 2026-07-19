package com.hiresemble.document.application;

public final class ObjectStorageException extends RuntimeException {

    public ObjectStorageException(Throwable cause) {
        super("Object storage operation failed", cause);
    }
}
