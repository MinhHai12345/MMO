package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.ingest.enums.Provider;
import java.util.List;

/** Read operations required by ingestion planning and season selection. */
public interface SeasonIngestionQueryPort {
    List<Season> findMappedSeasons(Provider provider);
    List<Season> findAllForActiveLeagues();
}
