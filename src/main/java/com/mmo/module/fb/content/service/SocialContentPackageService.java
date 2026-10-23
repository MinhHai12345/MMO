package com.mmo.module.fb.content.service;

import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface SocialContentPackageService {

    SocialContentPackage generate(Long matchId, DraftFormat format);

    record SocialContentPackage(
            Long matchId,
            String phase,
            String competition,
            Instant kickoff,
            String homeTeam,
            String awayTeam,
            String result,
            DraftFormat format,
            String editorialStatus,
            String title,
            String openingHook,
            List<String> analysisPoints,
            List<Probability> outcomeProbabilities,
            String modelDisclaimer,
            List<String> evidence,
            List<String> caveats,
            String instagramCaption,
            String facebookPost,
            String xPost,
            String youtubeDescription,
            String shortVideoScript,
            List<String> hashtags) {
        public SocialContentPackage {
            analysisPoints = analysisPoints == null ? List.of() : List.copyOf(analysisPoints);
            outcomeProbabilities = outcomeProbabilities == null ? List.of() : List.copyOf(outcomeProbabilities);
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            caveats = caveats == null ? List.of() : List.copyOf(caveats);
            hashtags = hashtags == null ? List.of() : List.copyOf(hashtags);
        }
    }

    record Probability(String outcome, BigDecimal percentage) {}
}
