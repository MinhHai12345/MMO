package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.league.entity.League;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves provider league names to an existing MMO league without treating provider
 * codes, slugs, or numeric IDs as globally interchangeable.
 *
 * <p>External IDs are resolved first through {@code fb_league_external_ids}. This matcher
 * is only the bootstrap fallback for a provider whose ID has not been mapped yet.</p>
 */
final class LeagueIdentityMatcher {

    private static final Map<String, String> NAME_ALIASES = Map.ofEntries(
            Map.entry("epl", "premierleague"),
            Map.entry("englishpremierleague", "premierleague"),
            Map.entry("premierleague", "premierleague"),
            Map.entry("laliga", "laliga"),
            Map.entry("primeradivision", "laliga"),
            Map.entry("spanishlaliga", "laliga"),
            Map.entry("bundesliga", "bundesliga"),
            Map.entry("germanbundesliga", "bundesliga"),
            Map.entry("seriea", "seriea"),
            Map.entry("italianseriea", "seriea"),
            Map.entry("ligue1", "ligue1"),
            Map.entry("frenchligue1", "ligue1"),
            Map.entry("uefachampionsleague", "uefachampionsleague"),
            Map.entry("championsleague", "uefachampionsleague"),
            Map.entry("ucl", "uefachampionsleague"),
            Map.entry("uefaeuropaleague", "uefaeuropaleague"),
            Map.entry("europaleague", "uefaeuropaleague"),
            Map.entry("uel", "uefaeuropaleague")
    );

    private static final Map<String, String> COUNTRY_ALIASES = Map.ofEntries(
            Map.entry("england", "unitedkingdom"),
            Map.entry("uk", "unitedkingdom"),
            Map.entry("gb", "unitedkingdom"),
            Map.entry("greatbritain", "unitedkingdom"),
            Map.entry("unitedkingdom", "unitedkingdom"),
            Map.entry("usa", "unitedstates"),
            Map.entry("us", "unitedstates"),
            Map.entry("unitedstatesofamerica", "unitedstates"),
            Map.entry("unitedstates", "unitedstates")
    );

    private LeagueIdentityMatcher() {
    }

    static boolean matches(League canonical, ProviderLeague source) {
        if (canonical == null || source == null || !sameName(canonical.getName(), source.name())) {
            return false;
        }

        String canonicalCountry = canonicalCountry(canonical.getCountry());
        String sourceCountry = canonicalCountry(source.country());

        // A missing country is not evidence of a conflict, but two known countries must agree.
        return canonicalCountry.isEmpty() || sourceCountry.isEmpty()
                || canonicalCountry.equals(sourceCountry);
    }

    private static boolean sameName(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return false;
        }
        return canonicalName(left).equals(canonicalName(right));
    }

    private static String canonicalName(String value) {
        String normalized = normalize(value);
        return NAME_ALIASES.getOrDefault(normalized, normalized);
    }

    private static String canonicalCountry(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = normalize(value);
        return COUNTRY_ALIASES.getOrDefault(normalized, normalized);
    }

    private static String normalize(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
