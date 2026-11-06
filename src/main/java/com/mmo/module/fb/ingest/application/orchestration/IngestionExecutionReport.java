package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.enums.CheckpointStatus;

import java.util.List;

/** Aggregate result of independent work-unit executions. */
public record IngestionExecutionReport(List<IngestionUnitResult> results) {

    public IngestionExecutionReport {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public long completed() {
        return count(CheckpointStatus.COMPLETED);
    }

    public long unavailable() {
        return count(CheckpointStatus.NOT_AVAILABLE);
    }

    public long partial() {
        return count(CheckpointStatus.PARTIAL);
    }

    public long failed() {
        return count(CheckpointStatus.FAILED);
    }

    public long skipped() {
        return results.stream().filter(IngestionUnitResult::skipped).count();
    }

    public long retryableFailures() {
        return results.stream().filter(IngestionUnitResult::isRetryable).count();
    }

    public boolean isSuccessful() {
        return results.stream().allMatch(IngestionUnitResult::isSuccessful);
    }

    public String summary() {
        return "units=" + results.size()
                + ", completed=" + completed()
                + ", unavailable=" + unavailable()
                + ", skipped=" + skipped()
                + ", partial=" + partial()
                + ", failed=" + failed()\n                + ", retryableFailures=" + retryableFailures();
    }

    public void throwIfIncomplete(String workflow) {
        List<String> errors = results.stream()
                .filter(result -> !result.isSuccessful())
                .map(result -> result.unit().provider() + "/" + result.unit().dataset()
                        + "/league=" + result.unit().leagueId()
                        + "/season=" + result.unit().season()
                        + " status=" + result.status() + " code=" + result.failureCode()\n                        + " retryable=" + result.retryable() + " reason=" + result.message())
                .toList();
        if (!errors.isEmpty()) {
            throw new IllegalStateException(workflow + " incomplete: " + summary() + "; errors=" + errors);
        }
    }

    private long count(CheckpointStatus status) {
        return results.stream().filter(result -> result.status() == status).count();
    }
}
