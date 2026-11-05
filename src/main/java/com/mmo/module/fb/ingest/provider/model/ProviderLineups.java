package com.mmo.module.fb.ingest.provider.model;

import java.util.List;

/** Provider-neutral lineups for a canonical fixture. */
public record ProviderLineups(
        List<ProviderLineupPlayer> home,
        List<ProviderLineupPlayer> away
) {
    public ProviderLineups {
        home = home == null ? List.of() : List.copyOf(home);
        away = away == null ? List.of() : List.copyOf(away);
    }

    public boolean isEmpty() {
        return home.isEmpty() && away.isEmpty();
    }
}
