package com.mmo.module.fb.ingest.provider.model;

import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared, provider-neutral normalization for values mapped into MMO models.
 *
 * <p>Provider IDs remain untouched. Team-name keys are conservative comparison
 * keys only; callers must require an unambiguous canonical-team match before
 * registering a cross-provider external-ID relationship.</p>
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
    /** Canonical popular club names and provider aliases shared by all TeamMappers. */
    private static final Map<String, String> TEAM_NAME_ALIASES = Map.ofEntries(
            Map.entry("1. FC Heidenheim 1846", "Heidenheim"),
            Map.entry("FC Heidenheim", "Heidenheim"),
            Map.entry("1. FC Köln", "FC Köln"),
            Map.entry("FC Cologne", "FC Köln"),
            Map.entry("1. FC Union Berlin", "Union Berlin"),
            Map.entry("Union Berlin", "Union Berlin"),
            Map.entry("1. FSV Mainz 05", "Mainz 05"),
            Map.entry("Mainz 05", "Mainz 05"),
            Map.entry("Bayer 04 Leverkusen", "Bayer Leverkusen"),
            Map.entry("Bayer Leverkusen", "Bayer Leverkusen"),
            Map.entry("FC Bayern München", "Bayern Munich"),
            Map.entry("Bayern Munich", "Bayern Munich"),
            Map.entry("Borussia M.Gladbach", "Borussia Mönchengladbach"),
            Map.entry("Borussia Mönchengladbach", "Borussia Mönchengladbach"),
            Map.entry("VfL Bochum 1848", "Bochum"),
            Map.entry("Bochum", "Bochum"),
            Map.entry("RasenBallsport Leipzig", "RB Leipzig"),
            Map.entry("RB Leipzig", "RB Leipzig"),
            Map.entry("SC Paderborn 07", "Paderborn"),
            Map.entry("Paderborn", "Paderborn"),
            Map.entry("SV 07 Elversberg", "Elversberg"),
            Map.entry("Elversberg", "Elversberg"),
            Map.entry("SV Darmstadt 98", "Darmstadt"),
            Map.entry("Darmstadt", "Darmstadt"),
            Map.entry("FC St. Pauli 1910", "St. Pauli"),
            Map.entry("St. Pauli", "St. Pauli"),
            Map.entry("VfL Wolfsburg", "Wolfsburg"),
            Map.entry("Wolfsburg", "Wolfsburg"),
            Map.entry("AC Milan", "AC Milan"),
            Map.entry("Milan", "AC Milan"),
            Map.entry("FC Internazionale Milano", "Inter"),
            Map.entry("Inter", "Inter"),
            Map.entry("Atalanta BC", "Atalanta"),
            Map.entry("Atalanta", "Atalanta"),
            Map.entry("Bologna FC 1909", "Bologna"),
            Map.entry("Bologna", "Bologna"),
            Map.entry("AC Monza", "Monza"),
            Map.entry("Monza", "Monza"),
            Map.entry("AC Pisa 1909", "Pisa"),
            Map.entry("Pisa", "Pisa"),
            Map.entry("ACF Fiorentina", "Fiorentina"),
            Map.entry("Fiorentina", "Fiorentina"),
            Map.entry("Cagliari Calcio", "Cagliari"),
            Map.entry("Cagliari", "Cagliari"),
            Map.entry("Como 1907", "Como"),
            Map.entry("Como", "Como"),
            Map.entry("US Cremonese", "Cremonese"),
            Map.entry("Cremonese", "Cremonese"),
            Map.entry("Frosinone Calcio", "Frosinone"),
            Map.entry("Frosinone", "Frosinone"),
            Map.entry("Genoa CFC", "Genoa"),
            Map.entry("Genoa", "Genoa"),
            Map.entry("Hellas Verona FC", "Verona"),
            Map.entry("Verona", "Verona"),
            Map.entry("Juventus FC", "Juventus"),
            Map.entry("Juventus", "Juventus"),
            Map.entry("SS Lazio", "Lazio"),
            Map.entry("Lazio", "Lazio"),
            Map.entry("US Lecce", "Lecce"),
            Map.entry("Lecce", "Lecce"),
            Map.entry("Parma Calcio 1913", "Parma"),
            Map.entry("Parma", "Parma"),
            Map.entry("SSC Napoli", "Napoli"),
            Map.entry("Napoli", "Napoli"),
            Map.entry("US Salernitana 1919", "Salernitana"),
            Map.entry("Salernitana", "Salernitana"),
            Map.entry("US Sassuolo Calcio", "Sassuolo"),
            Map.entry("Sassuolo", "Sassuolo"),
            Map.entry("Udinese Calcio", "Udinese"),
            Map.entry("Udinese", "Udinese"),
            Map.entry("AJ Auxerre", "Auxerre"),
            Map.entry("Auxerre", "Auxerre"),
            Map.entry("Angers SCO", "Angers"),
            Map.entry("Angers", "Angers"),
            Map.entry("Clermont Foot 63", "Clermont Foot"),
            Map.entry("Clermont Foot", "Clermont Foot"),
            Map.entry("Stade Brestois 29", "Brest"),
            Map.entry("Brest", "Brest"),
            Map.entry("Le Havre AC", "Le Havre"),
            Map.entry("Le Havre", "Le Havre"),
            Map.entry("Lille OSC", "Lille"),
            Map.entry("Lille", "Lille"),
            Map.entry("Racing Club de Lens", "Lens"),
            Map.entry("Lens", "Lens"),
            Map.entry("Olympique Lyonnais", "Lyon"),
            Map.entry("Lyon", "Lyon"),
            Map.entry("Olympique de Marseille", "Marseille"),
            Map.entry("Marseille", "Marseille"),
            Map.entry("Montpellier HSC", "Montpellier"),
            Map.entry("Montpellier", "Montpellier"),
            Map.entry("OGC Nice", "Nice"),
            Map.entry("Nice", "Nice"),
            Map.entry("Paris Saint-Germain FC", "Paris Saint-Germain"),
            Map.entry("Paris FC", "Paris FC"),
            Map.entry("Stade de Reims", "Reims"),
            Map.entry("Reims", "Reims"),
            Map.entry("Stade Rennais FC 1901", "Rennes"),
            Map.entry("Rennes", "Rennes"),
            Map.entry("RC Strasbourg Alsace", "Strasbourg"),
            Map.entry("Strasbourg", "Strasbourg"),
            Map.entry("ES Troyes AC", "Troyes"),
            Map.entry("Troyes", "Troyes"),
            Map.entry("Toulouse FC", "Toulouse"),
            Map.entry("Toulouse", "Toulouse"),
            Map.entry("FC Lorient", "Lorient"),
            Map.entry("FC Metz", "Metz"),
            Map.entry("FC Nantes", "Nantes"),
            Map.entry("AS Saint-Étienne", "Saint-Étienne"),
            Map.entry("Le Mans FC", "Le Mans"),
            Map.entry("Deportivo Alavés", "Alavés"),
            Map.entry("Alaves", "Alavés"),
            Map.entry("UD Almería", "Almería"),
            Map.entry("Almeria", "Almería"),
            Map.entry("Club Atlético de Madrid", "Atletico Madrid"),
            Map.entry("Atletico Madrid", "Atletico Madrid"),
            Map.entry("CA Osasuna", "Osasuna"),
            Map.entry("Osasuna", "Osasuna"),
            Map.entry("Cádiz CF", "Cádiz"),
            Map.entry("Cádiz", "Cádiz"),
            Map.entry("RC Celta de Vigo", "Celta Vigo"),
            Map.entry("Celta Vigo", "Celta Vigo"),
            Map.entry("CD Leganés", "Leganés"),
            Map.entry("Leganes", "Leganés"),
            Map.entry("Elche CF", "Elche"),
            Map.entry("Rayo Vallecano de Madrid", "Rayo Vallecano"),
            Map.entry("Racing Santander", "Racing Santander"),
            Map.entry("Real Racing Club de Santander", "Racing Santander"),
            Map.entry("RC Deportivo La Coruña", "Deportivo La Coruña"),
            Map.entry("Deportivo La Coruna", "Deportivo La Coruña"),
            Map.entry("RCD Espanyol de Barcelona", "Espanyol"),
            Map.entry("Espanyol", "Espanyol"),
            Map.entry("Girona FC", "Girona"),
            Map.entry("Granada CF", "Granada"),
            Map.entry("UD Las Palmas", "Las Palmas"),
            Map.entry("Las Palmas", "Las Palmas"),
            Map.entry("Levante UD", "Levante"),
            Map.entry("Levante", "Levante"),
            Map.entry("Málaga CF", "Málaga"),
            Map.entry("Mallorca", "Mallorca"),
            Map.entry("RCD Mallorca", "Mallorca"),
            Map.entry("Real Betis Balompié", "Real Betis"),
            Map.entry("Real Betis", "Real Betis"),
            Map.entry("Real Madrid CF", "Real Madrid"),
            Map.entry("Real Madrid", "Real Madrid"),
            Map.entry("Real Sociedad de Fútbol", "Real Sociedad"),
            Map.entry("Real Sociedad", "Real Sociedad"),
            Map.entry("Real Valladolid CF", "Real Valladolid"),
            Map.entry("Sevilla FC", "Sevilla"),
            Map.entry("Valencia CF", "Valencia"),
            Map.entry("Villarreal CF", "Villarreal"),
            Map.entry("AFC Bournemouth", "Bournemouth"),
            Map.entry("Bournemouth", "Bournemouth"),
            Map.entry("Brighton & Hove Albion FC", "Brighton"),
            Map.entry("Brighton", "Brighton"),
            Map.entry("Brentford FC", "Brentford"),
            Map.entry("Burnley FC", "Burnley"),
            Map.entry("Arsenal FC", "Arsenal"),
            Map.entry("Aston Villa FC", "Aston Villa"),
            Map.entry("Chelsea FC", "Chelsea"),
            Map.entry("Crystal Palace FC", "Crystal Palace"),
            Map.entry("Everton FC", "Everton"),
            Map.entry("Fulham FC", "Fulham"),
            Map.entry("Ipswich Town FC", "Ipswich Town"),
            Map.entry("Ipswich", "Ipswich Town"),
            Map.entry("Leeds United FC", "Leeds United"),
            Map.entry("Leeds", "Leeds United"),
            Map.entry("Leicester City FC", "Leicester City"),
            Map.entry("Leicester", "Leicester City"),
            Map.entry("Liverpool FC", "Liverpool"),
            Map.entry("Luton Town FC", "Luton Town"),
            Map.entry("Luton", "Luton Town"),
            Map.entry("Manchester City FC", "Manchester City"),
            Map.entry("Manchester United FC", "Manchester United"),
            Map.entry("Newcastle United FC", "Newcastle United"),
            Map.entry("Nottingham Forest FC", "Nottingham Forest"),
            Map.entry("Sheffield United FC", "Sheffield United"),
            Map.entry("Southampton FC", "Southampton"),
            Map.entry("Sunderland AFC", "Sunderland"),
            Map.entry("Tottenham Hotspur FC", "Tottenham"),
            Map.entry("Tottenham", "Tottenham"),
            Map.entry("West Ham United FC", "West Ham"),
            Map.entry("West Ham", "West Ham"),
            Map.entry("Wolverhampton Wanderers FC", "Wolves"),
            Map.entry("Wolverhampton Wanderers", "Wolves"),
            Map.entry("Hull City AFC", "Hull City"),
            Map.entry("Hull", "Hull City"),
            Map.entry("Coventry City FC", "Coventry City"),
            Map.entry("Coventry", "Coventry City")
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

    /**
     * Standardizes a provider team name for storage and downstream presentation.
     *
     * <p>Preserves the provider's spelling, accents, case and meaningful club
     * designators. It only applies Unicode NFC normalization, trims/collapses
     * whitespace and normalizes equivalent Unicode dash/apostrophe characters.
     * Identity matching is deliberately handled separately by
     * {@link #teamNameMatchKey(String)}.</p>
     */
    public static String standardTeamName(String sourceName) {
        String normalized = displayText(sourceName);
        if (normalized == null) return null;

        String unicode = Normalizer.normalize(normalized, Normalizer.Form.NFC)
                .replaceAll("[\\u2010-\\u2015\\u2212]", "-")
                .replace("‘", "'")
                .replace("’", "'")
                .replace("‛", "'")
                .replace('“', '"')
                .replace('”', '"');
        String displayName = displayText(unicode);
        return TEAM_NAME_ALIASES.getOrDefault(teamAliasKey(displayName), displayName);
    }

    /** Backward-compatible alias for callers using the previous method name. */
    public static String teamName(String sourceName) {
        return standardTeamName(sourceName);
    }

    /**
     * Produces a conservative comparison key for cross-provider team-name matching.
     * It ignores accents, punctuation, whitespace and common club designators,
     * but does not perform fuzzy matching or remove meaningful name tokens.
     */
    public static String teamNameMatchKey(String sourceName) {
        String normalized = standardTeamName(sourceName);
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
        String shortName = standardTeamName(sourceShortName);
        return shortName == null ? standardTeamName(sourceName) : shortName;
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

    private static String teamAliasKey(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String aliasKey(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
