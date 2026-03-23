package com.mmo.understat.service;

import com.mmo.understat.model.LeagueData;

public interface UnderStatService {

    LeagueData getLeagueData(String leagueName, String season);

}
