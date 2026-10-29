package com.mmo.module.fb.ingest.provider.model;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Converts provider display values to MMO's shared display convention.
 *
 * <p>This is deliberately not an identity resolver. Cross-provider entity identity
 * must come from explicit external-ID mappings, never from normalized names.</p>
 */
public final class MmoProviderDataStandardizer {

    private static final Map<String, String> LEAGUE_ALIASES = Map.ofEntries(
            Map.entry("la liga", "LaLiga"),
            Map.entry("laliga", "LaLiga"),
            Map.entry("primera division", "LaLiga"),
            Map.entry("premier league", "Premier League"),
            Map.entry("english premier league", "Premier League"),
            Map.entry("bundesliga", "Bundesliga"),
            Map.entry("serie a", "Serie A"),
            Map.entry("ligue 1", "Ligue 1")
    );

    private MmoProviderDataStandardizer() {
    }

    public static String leagueName(String sourceName) {
        String normalized = displayText(sourceName);
        if (normalized == null) {
            return null;
        }
        return LEAGUE_ALIASES.getOrDefault(aliasKey(normalized), normalized);
    }

    public static String teamName(String sourceName) {
        return displayText(sourceName);
    }

    public static String teamShortName(String sourceShortName, String sourceName) {
        String shortName = displayText(sourceShortName);
        return shortName == null ? teamName(sourceName) : shortName;
    }

    private static String displayText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String aliasKey(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
