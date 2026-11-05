package com.mmo.module.fb.ingest.application.port.in;

import com.mmo.module.fb.ingest.application.model.SofaScoreExternalMappingResult;

public interface SofaScoreExternalMappingUseCase {
    SofaScoreExternalMappingResult mapExistingCoreData();
}
