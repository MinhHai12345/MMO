package com.mmo.module.fb.ingest.application.port.out;

/** Outbound port for mapping provider-supplied names to MMO canonical display names. */
public interface MmoDataStandardizationPort {
    String leagueName(String providerName);
    String teamName(String providerName);
    String teamNameMatchKey(String providerName);
}
