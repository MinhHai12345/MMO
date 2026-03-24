package com.mmo.module.fb.understat.service;

import com.mmo.module.fb.understat.model.LeagueData;

public interface UnderStatService {

    LeagueData getLeagueData(String leagueName, String season);

}
