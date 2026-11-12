package com.mmo.module.fb.ingest.application.port.in;

import com.mmo.module.fb.ingest.application.model.SofaScoreDiagnosticRequest;
import com.mmo.module.fb.ingest.application.model.SofaScoreDiagnosticResult;

public interface SofaScoreDiagnosticUseCase {
    SofaScoreDiagnosticResult diagnose(SofaScoreDiagnosticRequest request);
}
