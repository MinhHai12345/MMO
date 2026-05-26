package com.mmo.module.fb.crawler.mapper;

import com.mmo.converter.AbstractMapper;
import com.mmo.module.fb.crawler.model.sofa.SofaSeasonData;
import com.mmo.module.fb.entity.Season;
import org.springframework.stereotype.Component;

@Component
public class SeasonDTO2SeasonMapper extends AbstractMapper<SofaSeasonData.SeasonDTO, Season> {

    @Override
    public Season map(SofaSeasonData.SeasonDTO source, Season target) {
        target.setSofaScoreId(source.getId());
        target.setYear(source.getYear());
        target.setCurrent(source.isCurrent());
        return target;
    }
}
