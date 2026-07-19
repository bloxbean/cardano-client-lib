package com.bloxbean.cardano.vds.jmt.integrity;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Structured integrity finding. */
public final class JmtIntegrityIssue {

    public enum Severity {
        WARNING,
        ERROR
    }

    private final Severity severity;
    private final String code;
    private final String message;
    private final Long version;
    private final String path;

    public JmtIntegrityIssue(Severity severity,
                             String code,
                             String message,
                             Long version,
                             String path) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.code = Objects.requireNonNull(code, "code");
        this.message = Objects.requireNonNull(message, "message");
        this.version = version;
        this.path = path;
    }

    public Severity severity() {
        return severity;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public OptionalLong version() {
        return version == null ? OptionalLong.empty() : OptionalLong.of(version);
    }

    public Optional<String> path() {
        return Optional.ofNullable(path);
    }
}
