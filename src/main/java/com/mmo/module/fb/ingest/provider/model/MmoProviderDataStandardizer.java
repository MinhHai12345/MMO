package com.mmo.module.fb.ingest.provider.model;

import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared, provider-neutral normalization for values mapped into MMO models.
 *
 * <p>This normalizes presentation and common categorical values only. It never
 * infers entity identity: provider IDs remain untouched and cross-provider
 * relationships must use explicit external-ID mappings.</p>
 */
public final class MmoProviderDataStandardizer {
    private static final Map<String, String> LEAGUE_ALIASES = Map.ofEntries(
            Map.entry("la liga", "LaLiga"),
            Map.entry("laliga", "LaLiga"),
            Map.entry("primera division", "LaLiga"),
            Map.entry("primera división", "LaLiga"),
            Map.entry("premier league", "Premier League"),
            Map.entry("english premier league", "Premier League"),
            Map.entry("bundesliga", "Bundesliga"),
            Map.entry("serie a", "Serie A"),
            Map.entry("ligue 1", "Ligue 1")
    );
    private static final Set<String> TEAM_NAME_DESIGNATORS = Set.of(
            "fc", "afc", "cf", "sc", "ac", "as", "sv", "fk", "sk",
            "club", "football", "soccer"
    );

    private MmoProviderDataStandardizer() {
    }

    public static String displayText(String value) {
        return StringUtils.hasText(value) ? value.trim().replaceAll("\\s+", " ") : null;
    }

    public static String leagueName(String sourceName) {
        String normalized = displayText(sourceName);
        return normalized == null ? null : LEAGUE_ALIASES.getOrDefault(aliasKey(normalized), normalized);
    }

    public static String teamName(String sourceName) {
        return displayText(sourceName);
    }

    /**
     * Produces a conservative comparison key for cross-provider team-name matching.
     * It ignores accents, punctuation, whitespace and common club designators,
     * but does not perform fuzzy matching or remove meaningful name tokens.
     */
    public static String teamNameMatchKey(String sourceName) {
        String normalized = displayText(sourceName);
        if (normalized == null) return null;

        String ascii = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (ascii.isEmpty()) return null;

        return java.util.Arrays.stream(ascii.split("\\s+"))
                .filter(token -> !TEAM_NAME_DESIGNATORS.contains(token))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    public static String teamShortName(String sourceShortName, String sourceName) {
        String shortName = displayText(sourceShortName);
        return shortName == null ? teamName(sourceName) : shortName;
    }

    public static String countryName(String sourceName) { return displayText(sourceName); }
    public static String roundName(String sourceName) { return displayText(sourceName); }
    public static String venueName(String sourceName) { return displayText(sourceName); }
    public static String refereeName(String sourceName) { return displayText(sourceName); }
    public static String bookmakerName(String sourceName) { return displayText(sourceName); }

    /** Normalize common provider statuses to the vocabulary used by MMO. */
    public static String fixtureStatus(String sourceStatus) {
        String normalized = displayText(sourceStatus);
        if (normalized == null) return null;
        return switch (aliasKey(normalized).replace(" ", "").replace("_", "").replace("-", "")) {
            case "notstarted", "scheduled", "tobestarted", "ns", "tbd", "timed" -> "NS";
            case "inprogress", "inplay", "live", "1h", "2h", "ht", "et", "pen", "p" -> "LIVE";
            case "finished", "fulltime", "ft", "aet", "finishedafterextratime",
                    "finishedafterpenalties" -> "FT";
            case "postponed", "pst" -> "PST";
            case "cancelled", "canceled", "canc" -> "CANC";
            case "suspended", "susp" -> "SUSP";
            case "interrupted", "int" -> "INT";
            case "abandoned", "abd" -> "ABD";
            case "awarded", "awd" -> "AWD";
            default -> normalized.toUpperCase(Locale.ROOT);
        };
    }

    public static String oddsMarketName(String sourceMarket) {
        String normalized = displayText(sourceMarket);
        if (normalized == null) return null;
        String key = aliasKey(normalized);
        if (key.contains("full time") || key.contains("match winner")
                || key.equals("1x2") || key.equals("winner")) return "Match Winner";
        if (key.contains("over/under") || key.contains("over under")
                || key.contains("total goals") || key.equals("goals")) return "Goals Over/Under";
        if (key.contains("both teams")) return "Both Teams Score";
        return normalized;
    }

    public static String oddsOutcomeName(String sourceOutcome) {
        String normalized = displayText(sourceOutcome);
        if (normalized == null) return null;
        return switch (aliasKey(normalized)) {
            case "1", "home", "team 1", "home win" -> "Home";
            case "x", "draw", "tie" -> "Draw";
            case "2", "away", "team 2", "away win" -> "Away";
            case "over 2.5", "over 2,5" -> "Over 2.5";
            case "under 2.5", "under 2,5" -> "Under 2.5";
            case "yes" -> "Yes";
            case "no" -> "No";
            default -> normalized;
        };
    }

    private static String aliasKey(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
