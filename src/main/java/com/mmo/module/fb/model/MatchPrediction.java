package com.mmo.module.fb.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MatchPrediction {
    private String league;
    private String homeName;
    private String awayName;
    private String matchTime;

    private double homeXG;
    private double awayXG;
    private String homeTeamForm;
    private String awayTeamForm;
    private MatchOddsInsight matchOddsInsight;
    private boolean homeFormStable;
    private boolean awayFormStable;
    private int confidence;

//    // 1. YouTube Script (Dạng Bullet points để đọc hoặc đưa vào Teleprompter)
//    private List<String> youtubeScriptHooks;
//    private String youtubeDescription;
//
//    // 2. Telegram Content (Dùng Markdown: Bold, Italic để dễ nhìn)
//    private String telegramPost;
//
//    // 3. Twitter/X (Ngắn gọn, nhiều Hashtag và Emoji)
//    private String twitterThread;
//
//    // 4. Reddit Analysis (Dạng bảng Markdown chuyên sâu cho r/soccerbetting)
//    private String redditMarkdown;
//
//    // 5. Affiliate Link & Call to Action
//    private String ctaLink; // Link Patreon hoặc Affiliate
}
