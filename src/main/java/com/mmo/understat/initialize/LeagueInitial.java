package com.mmo.understat.initialize;

import com.mmo.initialize.DataInitializer;
import com.mmo.repository.LeagueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LeagueInitial implements DataInitializer {
    private final LeagueRepository leagueRepository;

    private static final String EPL = "EPL";
    private static final String LA_LIGA = "La liga";
    private static final String BUNDESLIGA = "Bundesliga";
    private static final String SERIE_A = "Serie A";
    private static final String LIGUE_1 = "Ligue 1";
    private static final List<String> LEAGUES = List.of(EPL, LA_LIGA, BUNDESLIGA, SERIE_A, LIGUE_1);

    @Override
    public void initialize() {

    }
}
