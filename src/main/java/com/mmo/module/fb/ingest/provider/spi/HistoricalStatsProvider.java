package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.application.port.out.provider.HistoricalStatsIngestionProviderPort;

/**
 * @deprecated Implement the application-owned HistoricalStatsIngestionProviderPort contract directly.
 * This bridge is retained temporarily to keep existing provider adapters source-compatible.
 */
@Deprecated(forRemoval = false)
public interface HistoricalStatsProvider extends HistoricalStatsIngestionProviderPort {
}
