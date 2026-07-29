package com.bloxbean.cardano.vds.jmt.integrity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of a read-only JMT integrity check. */
public final class JmtIntegrityReport {

    private final JmtIntegrityMode mode;
    private final List<JmtIntegrityIssue> issues;
    private final long rootsChecked;
    private final long nodesChecked;
    private final long valuesChecked;
    private final boolean truncated;
    private final boolean cancelled;

    JmtIntegrityReport(JmtIntegrityMode mode,
                       List<JmtIntegrityIssue> issues,
                       long rootsChecked,
                       long nodesChecked,
                       long valuesChecked,
                       boolean truncated,
                       boolean cancelled) {
        this.mode = mode;
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        this.rootsChecked = rootsChecked;
        this.nodesChecked = nodesChecked;
        this.valuesChecked = valuesChecked;
        this.truncated = truncated;
        this.cancelled = cancelled;
    }

    public JmtIntegrityMode mode() {
        return mode;
    }

    public List<JmtIntegrityIssue> issues() {
        return issues;
    }

    public long rootsChecked() {
        return rootsChecked;
    }

    public long nodesChecked() {
        return nodesChecked;
    }

    public long valuesChecked() {
        return valuesChecked;
    }

    public boolean truncated() {
        return truncated;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public boolean healthy() {
        return !truncated && !cancelled && issues.stream()
                .noneMatch(issue -> issue.severity() == JmtIntegrityIssue.Severity.ERROR);
    }
}
