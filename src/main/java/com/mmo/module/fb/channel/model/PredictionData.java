package com.mmo.module.fb.channel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PredictionData {
    private int index;
    private String homeTeam;
    private String awayTeam;
    private String matchTime;

    // Số liệu Market dạng String để cố định số ký tự thập phân
    private String sofaHomeOdd;
    private String sofaDrawOdd;
    private String sofaAwayOdd;
    private String marketHomeXG;
    private String marketAwayXG;

    // Số liệu hệ thống H2 dạng String
    private String fairHomeOdd;
    private String fairDrawOdd;
    private String fairAwayOdd;
    private String homeXG;
    private String awayXG;
    private String h2HandicapMargin;
    private String h2TotalXG;
    private String h2ProbOver25;
    private String h2ProbUnder25;
    private String topCorrectScores;

    private String mostLikelyOutcome;
    private String valueBetPick;
    private String smartStakingSize;
}
