package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreLineupsResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.MatchResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.MatchesResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.OddsResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.SeasonsResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.StandingsResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.StatisticsResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.TournamentsResponseDTO;

/**
 * Central catalog of SofaScore web pages and the API responses emitted by each page.
 * Keep URL generation here so every page navigation has one explicit endpoint contract.
 */
public final class SofaScorePageSpecs {
    private static final String FOOTBALL_PAGE = "%s/football";
    private static final String TOURNAMENT_PAGE = "%s/football/unique-tournament/%d";
    private static final String STANDINGS_PAGE = "%s/football/unique-tournament/%d/season/%d/standings";
    private static final String SEASON_PAGE = "%s/football/unique-tournament/%d/season/%d";
    private static final String MATCH_PAGE = "%s/football/match/%d";
    private static final String TEAM_HISTORY_PAGE = "%s/football/team/%s/%s";

    private static final String TOURNAMENTS_ENDPOINT = "/api/v1/config/top-unique-tournaments/%s/football";
    private static final String SEASONS_ENDPOINT = "unique-tournament/%d/seasons";
    private static final String STANDINGS_ENDPOINT = "unique-tournament/%d/season/%d/standings/total";
    private static final String ROUND_EVENTS_ENDPOINT = "unique-tournament/%d/season/%d/events/round/%d";
    private static final String SEASON_EVENTS_ENDPOINT = "unique-tournament/%d/season/%d/events/%s/%d";
    private static final String MATCH_STATISTICS_ENDPOINT = "event/%d/statistics";
    private static final String DAILY_ODDS_ENDPOINT = "/api/v1/sport/football/odds/1/%s";
    private static final String DAILY_EVENTS_ENDPOINT = "/api/v1/unique-tournament/%d/scheduled-events/%s";
    private static final String TEAM_HISTORY_ENDPOINT = "team/%s/events/last/%d";
    private static final String MATCH_ENDPOINT = "event/%d";
    private static final String LINEUPS_ENDPOINT = "event/%d/lineups";

    private SofaScorePageSpecs() {
    }

    public static SofaScoreLeaguePageSpec footballLeagueCatalogue(String webUrl, String region) {
        String normalizedRegion = region == null || !region.matches("[A-Za-z]{2}")
                ? "EN" : region.toUpperCase(java.util.Locale.ROOT);
        return new SofaScoreLeaguePageSpec(
                page(webUrl, FOOTBALL_PAGE),
                String.format(TOURNAMENTS_ENDPOINT, normalizedRegion),
                java.util.regex.Pattern.compile("^/api/v1/unique-tournament/(\\d+)/seasons$", 
                        java.util.regex.Pattern.CASE_INSENSITIVE));
    }

    public static SofaScorePageSpec footballCatalogue(String webUrl, String region) {
        String normalizedRegion = region == null || !region.matches("[A-Za-z]{2}")
                ? "EN" : region.toUpperCase(java.util.Locale.ROOT);
        return SofaScorePageSpec.of(
                page(webUrl, FOOTBALL_PAGE),
                SofaScoreEndpointSpec.required(
                        String.format(TOURNAMENTS_ENDPOINT, normalizedRegion), TournamentsResponseDTO.class));
    }

    public static SofaScorePageSpec footballCatalogueWithOdds(String webUrl, String region, String date) {
        SofaScorePageSpec catalogue = footballCatalogue(webUrl, region);
        return SofaScorePageSpec.of(
                catalogue.loadPageUrl(),
                catalogue.endpointSpecs().get(0),
                SofaScoreEndpointSpec.optional(dailyOddsEndpoint(date), OddsResponseDTO.class));
    }

    public static SofaScorePageSpec seasons(String webUrl, long tournamentId) {
        return SofaScorePageSpec.of(
                page(webUrl, TOURNAMENT_PAGE, tournamentId),
                SofaScoreEndpointSpec.required(
                        String.format(SEASONS_ENDPOINT, tournamentId), SeasonsResponseDTO.class));
    }

    public static SofaScorePageSpec standings(String webUrl, long tournamentId, long seasonId) {
        return SofaScorePageSpec.of(
                page(webUrl, STANDINGS_PAGE, tournamentId, seasonId),
                SofaScoreEndpointSpec.required(
                        String.format(STANDINGS_ENDPOINT, tournamentId, seasonId), StandingsResponseDTO.class));
    }

    public static SofaScorePageSpec roundEvents(
            String webUrl, long tournamentId, long seasonId, int round) {
        return SofaScorePageSpec.of(
                page(webUrl, SEASON_PAGE, tournamentId, seasonId),
                SofaScoreEndpointSpec.required(
                        String.format(ROUND_EVENTS_ENDPOINT, tournamentId, seasonId, round),
                        MatchesResponseDTO.class));
    }

    public static SofaScorePageSpec seasonEvents(
            String webUrl, long tournamentId, long seasonId, String direction, int pageIndex) {
        return SofaScorePageSpec.of(
                page(webUrl, SEASON_PAGE, tournamentId, seasonId),
                SofaScoreEndpointSpec.required(
                        String.format(SEASON_EVENTS_ENDPOINT, tournamentId, seasonId, direction, pageIndex),
                        MatchesResponseDTO.class));
    }

    public static SofaScorePageSpec matchStatistics(String webUrl, long matchId) {
        return SofaScorePageSpec.of(
                page(webUrl, MATCH_PAGE, matchId),
                SofaScoreEndpointSpec.required(
                        String.format(MATCH_STATISTICS_ENDPOINT, matchId), StatisticsResponseDTO.class),
                SofaScoreEndpointSpec.optional(
                        String.format(MATCH_ENDPOINT, matchId), MatchResponseDTO.class),
                SofaScoreEndpointSpec.optional(
                        lineupsEndpoint(matchId), SofaScoreLineupsResponseDTO.class));
    }

    public static SofaScorePageSpec matchDetails(String webUrl, long matchId) {
        return SofaScorePageSpec.of(
                page(webUrl, MATCH_PAGE, matchId),
                SofaScoreEndpointSpec.required(
                        String.format(MATCH_ENDPOINT, matchId), MatchResponseDTO.class),
                SofaScoreEndpointSpec.optional(
                        String.format(MATCH_STATISTICS_ENDPOINT, matchId), StatisticsResponseDTO.class),
                SofaScoreEndpointSpec.optional(
                        lineupsEndpoint(matchId), SofaScoreLineupsResponseDTO.class));
    }

    public static SofaScorePageSpec dailyTournamentEvents(String webUrl, long tournamentId, String date) {
        return SofaScorePageSpec.of(
                page(webUrl, TOURNAMENT_PAGE, tournamentId),
                SofaScoreEndpointSpec.required(
                        String.format(DAILY_EVENTS_ENDPOINT, tournamentId, date), MatchesResponseDTO.class));
    }

    public static SofaScorePageSpec dailyEventsWithOdds(String webUrl, long tournamentId, String date) {
        return SofaScorePageSpec.of(
                page(webUrl, TOURNAMENT_PAGE, tournamentId),
                SofaScoreEndpointSpec.required(
                        String.format(DAILY_EVENTS_ENDPOINT, tournamentId, date), MatchesResponseDTO.class),
                SofaScoreEndpointSpec.optional(dailyOddsEndpoint(date), OddsResponseDTO.class));
    }

    public static SofaScorePageSpec teamHistory(
            String webUrl, String teamSlug, String teamId, int pageIndex) {
        return SofaScorePageSpec.of(
                page(webUrl, TEAM_HISTORY_PAGE, teamSlug, teamId),
                SofaScoreEndpointSpec.required(
                        String.format(TEAM_HISTORY_ENDPOINT, teamId, pageIndex), MatchesResponseDTO.class));
    }

    public static SofaScorePageSpec lineups(String webUrl, long matchId) {
        return SofaScorePageSpec.of(
                page(webUrl, MATCH_PAGE, matchId),
                SofaScoreEndpointSpec.required(
                        String.format(MATCH_ENDPOINT, matchId), MatchResponseDTO.class),
                SofaScoreEndpointSpec.optional(
                        lineupsEndpoint(matchId), SofaScoreLineupsResponseDTO.class),
                SofaScoreEndpointSpec.optional(
                        String.format(MATCH_STATISTICS_ENDPOINT, matchId), StatisticsResponseDTO.class));
    }

    public static String dailyOddsEndpoint(String date) {
        return String.format(DAILY_ODDS_ENDPOINT, date);
    }

    public static String lineupsEndpoint(long matchId) {
        return String.format(LINEUPS_ENDPOINT, matchId);
    }

    private static String page(String webUrl, String template, Object... arguments) {
        if (webUrl == null || webUrl.isBlank()) {
            throw new IllegalArgumentException("SofaScore web URL must not be blank");
        }
        String baseUrl = webUrl.replaceAll("/+$", "");
        Object[] values = new Object[arguments.length + 1];
        values[0] = baseUrl;
        System.arraycopy(arguments, 0, values, 1, arguments.length);
        return String.format(template, values);
    }
}
