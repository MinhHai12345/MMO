package com.mmo.module.fb.ingest.provider.model;

/** Provider-neutral player identity and match-performance line. */
public record ProviderLineupPlayer(
        String externalPlayerId,
        String name,
        String position,
        String nationality,
        Integer height,
        Integer weight,
        ProviderPlayerMatchStats statistics
) {}
