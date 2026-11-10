package com.mmo.module.fb.analytics.service;

import java.time.Instant;
import java.util.List;

public interface DataQualityValidationService {
    DataQualityValidationReport validate();

    record DataQualityValidationReport(Instant generatedAt, String status,
                                       long criticalFindings, long warningFindings,
                                       List<ValidationFinding> findings) {
        public DataQualityValidationReport {
            findings = findings == null ? List.of() : List.copyOf(findings);
        }
    }

    record ValidationFinding(String severity, String code, String description, long affectedRows) {}
}
