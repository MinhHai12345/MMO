//package com.mmo.module.fb.understat.initialize;
//
//import com.mmo.module.fb.entity.League;
//import com.mmo.initialize.DataInitializer;
//import com.mmo.module.fb.repository.LeagueRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDate;
//import java.util.List;
//
//@Component
//@RequiredArgsConstructor
//public class LeagueInitial implements DataInitializer {
//    private final LeagueRepository leagueRepository;
//
//    private static final String EPL = "EPL";
//    private static final String LA_LIGA = "La liga";
//    private static final String BUNDESLIGA = "Bundesliga";
//    private static final String SERIE_A = "Serie A";
//    private static final String LIGUE_1 = "Ligue 1";
//    private static final List<String> LEAGUES = List.of(EPL, LA_LIGA, BUNDESLIGA, SERIE_A, LIGUE_1);
//
//    @Override
//    public void initialize() {
//        LocalDate now = LocalDate.now();
//        int currentYear = now.getYear();
//        int currentMonth = now.getMonthValue();
//        if (currentMonth < 8) {
//            return;
//        }
//        String currentSeason = currentYear + "/" + (currentYear + 1);
//
//        for (String leagueName : LEAGUES) {
//            boolean exists = leagueRepository.existsByNameAndSeason(leagueName, currentSeason);
//            if (!exists) {
//                League league = League.builder()
//                        .name(leagueName)
//                        .season(currentSeason)
//                        .build();
//                leagueRepository.save(league);
//            }
//        }
//
//    }
//}
