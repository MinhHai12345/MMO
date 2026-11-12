package com.mmo.module.fb.ingest.adapter.out.provider.sofascore;

import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.SeasonDTO;

import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Matches SofaScore season labels to the canonical start-year used by the domain model. */
public final class SofaScoreSeasonMatcher {
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(20\\d{2}|\\d{2})(?!\\d)");

    private SofaScoreSeasonMatcher() { }

    public static Optional<SeasonDTO> findMatchingSeason(List<SeasonDTO> seasons, String requestedSeason) {
        if (seasons == null || seasons.isEmpty()) return Optional.empty();
        return seasons.stream().filter(Objects::nonNull)
                .filter(season -> matches(requestedSeason, season)).findFirst();
    }

    public static boolean matches(String requestedSeason, SeasonDTO season) {
        if (season == null || requestedSeason == null || requestedSeason.isBlank()) return true;
        String requested = canonicalStartYear(requestedSeason);
        return equalsLabel(requestedSeason, season.year())
                || equalsLabel(requestedSeason, season.name())
                || (requested != null && (requested.equals(canonicalStartYear(season.year()))
                || requested.equals(canonicalStartYear(season.name()))));
    }

    /** Returns the season's four-digit start year, e.g. "26/27" -> "2026". */
    public static String canonicalStartYear(String label) {
        if (label == null || label.isBlank()) return null;
        Matcher matcher = YEAR.matcher(label);
        if (!matcher.find()) return null;
        String year = matcher.group(1);
        if (year.length() == 4) return year;
        int shortYear = Integer.parseInt(year);
        return String.valueOf(shortYear <= 79 ? 2000 + shortYear : 1900 + shortYear);
    }

    private static boolean equalsLabel(String expected, String actual) {
        return actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }
}
