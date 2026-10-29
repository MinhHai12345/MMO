package com.mmo.module.fb.ingest.provider.apifootball.mapper;

import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.StandingRowDto;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.StandingsItem;
import com.mmo.module.fb.ingest.provider.model.MmoProviderDataStandardizer;
import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ApiFootballStandingsMapper {

    private ProviderStanding map(StandingRowDto row) {
        if (row == null || row.team() == null || row.team().id() == null) return null;
        var all = row.all();
        var goals = all == null ? null : all.goals();
        return new ProviderStanding(
                row.team().id().toString(),
                row.rank(),
                row.points(),
                row.goalsDiff(),
                MmoProviderDataStandardizer.displayText(row.form()),
                MmoProviderDataStandardizer.displayText(row.group()),
                all == null ? null : all.played(),
                all == null ? null : all.win(),
                all == null ? null : all.draw(),
                all == null ? null : all.lose(),
                goals == null ? null : goals.forGoals(),
                goals == null ? null : goals.againstGoals()
        );
    }

    public List<ProviderStanding> toModels(List<StandingsItem> items) {
        List<ProviderStanding> result = new ArrayList<>();
        if (items == null) return result;
        for (StandingsItem item : items) {
            if (item == null || item.league() == null || item.league().standings() == null) continue;
            for (List<StandingRowDto> group : item.league().standings()) {
                if (group == null) continue;
                for (StandingRowDto row : group) {
                    ProviderStanding mapped = map(row);
                    if (mapped != null) result.add(mapped);
                }
            }
        }
        return result;
    }
}
