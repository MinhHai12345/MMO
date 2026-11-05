package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.application.port.out.provider.LeagueIngestionPort;

/**
 * @deprecated Implement the application-owned LeagueIngestionPort contract directly.
 * This bridge is retained temporarily to keep existing provider adapters source-compatible.
 */
@Deprecated(forRemoval = false)
public interface LeagueProvider extends LeagueIngestionPort {
}
