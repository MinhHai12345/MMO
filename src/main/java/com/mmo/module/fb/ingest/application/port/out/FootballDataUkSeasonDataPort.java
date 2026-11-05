package com.mmo.module.fb.ingest.application.port.out;

import java.util.List;
import java.util.Map;

/** Outbound port for retrieving a parsed Football-Data.co.uk season dataset. */
public interface FootballDataUkSeasonDataPort {
    List<Map<String, String>> fetchSeason(String baseUri, String season, String leagueCode);
}
