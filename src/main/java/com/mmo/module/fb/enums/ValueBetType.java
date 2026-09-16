package com.mmo.module.fb.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ValueBetType {
    HOME_WIN("Home Win"),
    DRAW("Draw"),
    AWAY_WIN("Away Win"),
    OVER_25("Over 2.5"),
    UNDER_25("Under 2.5"),
    HANDICAP_HOME("Handicap Home"),
    HANDICAP_AWAY("Handicap Away"),
    NONE("No Value");

    private final String displayName;

    /**
     * Kiểm tra xem loại kèo này có phải là kèo chính 1X2 hay không
     */
    public boolean isMatchResult1X2() {
        return this == HOME_WIN || this == DRAW || this == AWAY_WIN;
    }

    /**
     * Kiểm tra xem loại kèo này có thuộc thị trường Bàn thắng (Over/Under) hay không
     */
    public boolean isGoalsMarket() {
        return this == OVER_25 || this == UNDER_25;
    }
}
