package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.ingest.application.port.in.FootballDataUkIngestionUseCase;

/**
 * @deprecated This is an inbound use case, not an outbound persistence/provider port.
 * Use {@link FootballDataUkIngestionUseCase}. Kept temporarily for source compatibility.
 */
@Deprecated(forRemoval = true)
public interface FootballDataUkIngestionPort extends FootballDataUkIngestionUseCase {
}
