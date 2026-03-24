package com.mmo.module.crawler.mapper;

import com.mmo.converter.AbstractMapper;
import com.mmo.entity.PerformanceHistory;
import com.mmo.understat.model.LeagueData;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class PerformanceHistoryMapper extends AbstractMapper<LeagueData.TeamData.HistoryData, PerformanceHistory> {

    @Override
    public PerformanceHistory map(LeagueData.TeamData.HistoryData source, PerformanceHistory target) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        target.setDate(LocalDateTime.parse(source.getDate(), formatter));
        target.setDeep(source.getDeep());
        target.setDeepAllowed(source.getDeep_allowed());
        target.setMissed(source.getMissed());
        target.setScored(source.getScored());
        target.setXG(source.getXG());
        target.setXGA(source.getXGA());
        target.setNpxG(source.getNpxG());
        target.setNpxGA(source.getNpxGA());
        target.setNpxGD(source.getNpxGD());
        target.setHomeAway(source.getH_a());
        target.setResult(source.getResult());
        target.setXpts(source.getXpts());
        if (source.getPpda() != null) {
            target.setPpdaAtt(source.getPpda().getAtt());
            target.setPpdaDef(source.getPpda().getDef());
        }
        if (source.getPpdaAllowed() != null) {
            target.setPpdaAllowedAtt(source.getPpdaAllowed().getAtt());
            target.setPpdaAllowedDef(source.getPpdaAllowed().getDef());
        }
        return target;
    }
}
