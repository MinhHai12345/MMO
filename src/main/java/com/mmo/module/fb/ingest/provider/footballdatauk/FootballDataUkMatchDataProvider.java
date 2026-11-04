package com.mmo.module.fb.ingest.provider.footballdatauk;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Football-Data.co.uk source adapter. Owns provider URL conventions, CSV download and
 * parsing; application services receive parsed provider rows and never know transport details.
 */
@Component
@RequiredArgsConstructor
public class FootballDataUkMatchDataProvider {

    private final FootballDataUkCsvClient csvClient;
    private final FootballDataUkCsvParser csvParser;

    public List<Map<String, String>> fetchSeason(
            String baseUri, String season, String leagueCode) {
        String url = buildCsvUrl(baseUri, season, leagueCode);
        return csvParser.parse(csvClient.getCsv(url));
    }

    private String buildCsvUrl(String configuredBaseUri, String season, String leagueCode) {
        String base = StringUtils.hasText(configuredBaseUri)
                ? configuredBaseUri.trim() : "https://football-data.co.uk";
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if ("https://www.football-data.co.uk".equalsIgnoreCase(base)) {
            base = "https://football-data.co.uk";
        }
        String digits = season == null ? "" : season.replaceAll("\\D", "");
        if (digits.length() < 4) {
            throw new IllegalArgumentException("Unsupported Football-Data.co.uk season: " + season);
        }
        int startYear = Integer.parseInt(digits.substring(0, 4));
        String seasonCode = digits.substring(2, 4)
                + String.format(Locale.ROOT, "%02d", (startYear + 1) % 100);
        if (!StringUtils.hasText(leagueCode)) {
            throw new IllegalArgumentException("Football-Data.co.uk league code must not be blank");
        }
        return base + "/mmz4281/" + seasonCode + "/"
                + leagueCode.trim().toUpperCase(Locale.ROOT) + ".csv";
    }
}
