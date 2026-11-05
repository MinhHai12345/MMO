package com.mmo.module.fb.ingest.application.exception;

/** Provider-neutral access failure classification used by application orchestration. */
public class ProviderAccessException extends RuntimeException {
    public enum Kind {
        RESOURCE_RESTRICTED,
        PLAN_RESTRICTED,
        RATE_LIMITED
    }

    private final Kind kind;

    public ProviderAccessException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        if (kind == null) throw new IllegalArgumentException("Provider access failure kind must not be null");
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean is(Kind expected) {
        return kind == expected;
    }
}
