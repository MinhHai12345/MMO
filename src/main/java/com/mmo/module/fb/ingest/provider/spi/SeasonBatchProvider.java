package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.application.port.out.provider.SeasonBatchIngestionPort;

/**
 * @deprecated Implement the application-owned {@link SeasonBatchIngestionPort} directly.
 * Kept temporarily so existing provider adapters can migrate independently.
 */
@Deprecated(forRemoval = false)
public interface SeasonBatchProvider extends SeasonBatchIngestionPort {
}
