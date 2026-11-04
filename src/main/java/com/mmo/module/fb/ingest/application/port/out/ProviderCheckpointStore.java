package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.ingest.enums.Provider;

/** Application-owned checkpoint contract; persistence details stay in the adapter layer. */
public interface ProviderCheckpointStore {
    boolean isCompleted(Provider provider, String checkpointKey);
    void markCompleted(Provider provider, String checkpointKey);
}
