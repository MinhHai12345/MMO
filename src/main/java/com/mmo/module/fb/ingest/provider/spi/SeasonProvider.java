package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.application.port.out.provider.SeasonIngestionPort;

/**
 * @deprecated Implement the application-owned SeasonIngestionPort contract directly.
 * This bridge is retained temporarily to keep existing provider adapters source-compatible.
 */
@Deprecated(forRemoval = false)
public interface SeasonProvider extends SeasonIngestionPort {
}
