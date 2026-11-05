package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.application.port.out.provider.FixtureIngestionPort;

/**
 * @deprecated Implement the application-owned FixtureIngestionPort contract directly.
 * This bridge is retained temporarily to keep existing provider adapters source-compatible.
 */
@Deprecated(forRemoval = false)
public interface FixtureProvider extends FixtureIngestionPort {
}
