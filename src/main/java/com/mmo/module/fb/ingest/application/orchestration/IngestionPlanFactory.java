package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.application.port.out.SeasonIngestionQueryPort;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.policy.ProviderRoute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a deterministic plan from persisted seasons and DataSourcePolicy.
 * Each primary route and permitted secondary fallback becomes its own work unit.
 * No league IDs, season years or dataset provider choices are hard-coded in configuration.
 */
@Component
@RequiredArgsConstructor
public class IngestionPlanFactory {

    private static final List<Dataset> SEASON_SCOPED_DATASETS = List.of(
            Dataset.SEASON,
            Dataset.TEAM,
            Dataset.FIXTURE,
            Dataset.STANDINGS,
            Dataset.MATCH_STATISTICS,
            Dataset.MATCH_XG,
            Dataset.HISTORICAL_ODDS,
            Dataset.UPCOMING_ODDS
    );

    private final SeasonIngestionQueryPort seasonQueryPort;
    private final DataSourcePolicy dataSourcePolicy;

    public IngestionPlan activeSeasonPlan() {
        return create(seasonQueryPort.findAllForActiveLeagues().stream()
                .filter(Season::isActive)
                .toList());
    }

    public IngestionPlan historicalPlan() {
        return create(seasonQueryPort.findAllForActiveLeagues());
    }

    private IngestionPlan create(List<Season> seasons) {
        List<IngestionWorkUnit> units = new ArrayList<>();
        seasons.stream()
                .filter(this::isUsable)
                .sorted(Comparator.comparing((Season s) -> s.getLeague().getId())
                        .thenComparing(Season::getYear))
                .forEach(season -> SEASON_SCOPED_DATASETS.forEach(dataset ->
                        addRouteUnits(units, dataset, season)));
        return new IngestionPlan(units);
    }

    private void addRouteUnits(List<IngestionWorkUnit> units, Dataset dataset, Season season) {
        ProviderRoute route = dataSourcePolicy.route(dataset, season);
        if (route.primary() == null) return;
        units.add(new IngestionWorkUnit(dataset, season.getId(), season.getLeague().getId(),
                season.getYear(), route.primary(), IngestionWorkUnit.Role.PRIMARY));
        route.secondary().forEach(provider -> units.add(new IngestionWorkUnit(
                dataset, season.getId(), season.getLeague().getId(), season.getYear(),
                provider, IngestionWorkUnit.Role.SECONDARY)));
    }

    private boolean isUsable(Season season) {
        return season != null && season.getId() != null && season.getLeague() != null
                && season.getLeague().getId() != null
                && season.getYear() != null && !season.getYear().isBlank();
    }
}
