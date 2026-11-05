package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.application.port.out.provider.OddsIngestionPort;

/**
 * @deprecated Implement the application-owned OddsIngestionPort contract directly.
 * This bridge is retained temporarily to keep existing provider adapters source-compatible.
 */
@Deprecated(forRemoval = false)
public interface OddsProvider extends OddsIngestionPort {
}
