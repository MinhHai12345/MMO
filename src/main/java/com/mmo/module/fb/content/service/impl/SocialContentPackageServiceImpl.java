package com.mmo.module.fb.content.service.impl;

import com.mmo.module.fb.analytics.service.ContentDraftService;
import com.mmo.module.fb.analytics.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.MatchSocialInsight;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.PredictionInsight;
import com.mmo.module.fb.content.service.SocialContentPackageService;
import com.mmo.module.fb.content.service.SocialContentPackageService.Probability;
import com.mmo.module.fb.content.service.SocialContentPackageService.SocialContentPackage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialContentPackageServiceImpl implements SocialContentPackageService {

    private static final String DISCLAIMER =
            "Probabilities are model estimates, not guarantees. They are not betting advice. "
                    + "Actual outcomes may differ; check the data timestamp and limitations before publication.";

    private static final DateTimeFormatter KICKOFF_FORMAT = DateTimeFormatter
            .ofPattern("dd MMM yyyy HH:mm 'UTC'")
            .withLocale(Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private final MatchSocialAnalyticsService analyticsService;
    private final ContentDraftService draftService;
    private final ContentDraftQualityGate qualityGate;

    @Override
    public SocialContentPackage generate(Long matchId, DraftFormat requestedFormat) {
        if (matchId == null || matchId <= 0 || requestedFormat == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A positive matchId and format are required"
            );
        }

        MatchSocialInsight insight = analyticsService.analyze(matchId);
        String phase = resolvePhase(insight.status(), insight.postMatchAnalysis() != null);
        DraftFormat effectiveFormat = resolveFormat(requestedFormat, phase);

        ContentDraft draft = qualityGate.evaluate(draftService.generate(matchId, effectiveFormat));

        String home = safe(insight.home() == null ? null : insight.home().name(), "Home team");
        String away = safe(insight.away() == null ? null : insight.away().name(), "Away team");
        String fixture = home + " vs " + away;
        String kickoff = insight.kickoff() == null
                ? null
                : KICKOFF_FORMAT.format(insight.kickoff());
        String result = insight.postMatchAnalysis() == null
                ? null
                : insight.postMatchAnalysis().resultSummary();

        List<Probability> probabilities = "PRE_MATCH".equals(phase) || "LIVE".equals(phase)
                ? probabilities(insight.prediction())
                : List.of();

        Stream<String> postMatchFindings = insight.postMatchAnalysis() == null
                ? Stream.empty()
                : insight.postMatchAnalysis().evidenceBasedFindings().stream();

        List<String> analysisPoints = Stream.concat(
                        Stream.concat(draft.outline().stream(), insight.editorialAngles().stream()),
                        postMatchFindings
                )
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(8)
                .toList();

        List<String> caveats = Stream.concat(
                        draft.caveats().stream(),
                        insight.dataWarnings().stream()
                )
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));

        String probabilityLine = probabilities.isEmpty()
                ? null
                : probabilities.stream()
                        .map(probability -> probability.outcome() + " "
                                + probability.percentage().toPlainString() + "%")
                        .collect(Collectors.joining(" | "));

        String title = safe(draft.title(), fixture + ": football analysis");
        String opening = safe(draft.openingHook(), "A data-led look at " + fixture + ".");
        String analysis = analysisPoints.isEmpty()
                ? "The available dataset does not support a stronger conclusion yet."
                : analysisPoints.stream()
                        .limit(4)
                        .map(point -> "• " + point)
                        .collect(Collectors.joining("\n"));

        String probabilitySection = probabilityLine == null
                ? ""
                : "\nModel 1X2 estimates: " + probabilityLine + ".";

        String evidenceSection = draft.evidence().isEmpty()
                ? ""
                : "\nData notes: " + String.join("; ", draft.evidence().stream().limit(3).toList()) + ".";

        String caveatSection = caveats.isEmpty()
                ? ""
                : "\nLimitations: " + String.join("; ", caveats.stream().limit(2).toList()) + ".";

        String baseCopy = String.join(
                "\n\n",
                title,
                opening,
                analysis + probabilitySection + evidenceSection + caveatSection,
                DISCLAIMER
        );

        List<String> hashtagList = hashtagList(home, away, insight.competition());
        String hashtags = String.join(" ", hashtagList);

        String shortVideoScript;
        if (effectiveFormat == DraftFormat.LONG_VIDEO) {
            List<String> chapters = new ArrayList<>();
            chapters.add("INTRO (0:00-0:30): " + opening);
            int chapter = 1;
            for (String point : analysisPoints) {
                chapters.add("CHAPTER " + chapter++ + ": " + point
                        + "\nVisual: original tactical board, licensed footage, or verified data graphic."
                        + "\nTransition: explain which evidence supports this point and what remains uncertain.");
            }
            chapters.add("CONCLUSION: Summarize the strongest evidence, the main uncertainty, and invite viewers to discuss.");
            shortVideoScript = String.join("\n\n", chapters)
                    + "\n\nEDITOR NOTE: Add verified score/stat citations and review every claim before recording."
                    + "\n" + DISCLAIMER;
        } else if (effectiveFormat == DraftFormat.SHORTS) {
            String keyPoints = analysisPoints.stream().limit(3)
                    .map(point -> point + ".")
                    .collect(Collectors.joining("\n"));
            shortVideoScript = "0-3s | HOOK: " + opening
                    + "\n3-20s | ANALYSIS:\n" + keyPoints
                    + "\n20-27s | TAKEAWAY: One evidence-based insight; avoid certainty claims."
                    + "\n27-30s | CTA: What did you notice in this match?"
                    + probabilitySection + "\n" + DISCLAIMER;
        } else {
            shortVideoScript = opening + "\n\n"
                    + analysisPoints.stream().limit(3).map(point -> point + ".")
                    .collect(Collectors.joining("\n"))
                    + probabilitySection + "\n\n" + DISCLAIMER;
        }

        String instagramCopy = opening + "\n\n"
                + analysisPoints.stream().limit(3).map(point -> "• " + point)
                .collect(Collectors.joining("\n"))
                + probabilitySection + "\n\n" + hashtags;
        String facebookCopy = baseCopy + "\n\n"
                + "Join the discussion: which tactical detail mattered most?\n\n" + hashtags;
        String youtubeCopy = baseCopy + "\n\n"
                + "CHAPTERS\n00:00 Introduction\n"
                + (analysisPoints.isEmpty() ? "" : "00:30 Key analysis\n")
                + "Data note: estimates are model-generated; verify claims and source timestamps before publishing.\n"
                + hashtags;

        String editorialStatus = draft.status();
        if ("BLOCKED".equalsIgnoreCase(editorialStatus)) {
            caveats.add(0, "PUBLICATION BLOCKED: resolve all quality blockers before posting.");
        }

        return new SocialContentPackage(
                matchId,
                phase,
                insight.competition(),
                insight.kickoff(),
                home,
                away,
                result,
                effectiveFormat,
                editorialStatus,
                title,
                opening,
                analysisPoints,
                probabilities,
                DISCLAIMER,
                draft.evidence(),
                caveats,
                instagramCopy,
                facebookCopy,
                truncate(opening + " " + String.join(" ", analysisPoints)
                        + probabilitySection + " " + hashtags, 275),
                youtubeCopy,
                shortVideoScript,
                hashtagList
        );
    }

    private static DraftFormat resolveFormat(DraftFormat requestedFormat, String phase) {
        if ("POST_MATCH".equals(phase) && requestedFormat == DraftFormat.PRE_MATCH) {
            return DraftFormat.POST_MATCH;
        }
        return requestedFormat;
    }

    private static List<Probability> probabilities(PredictionInsight prediction) {
        if (prediction == null
                || prediction.homeWinProbability() == null
                || prediction.drawProbability() == null
                || prediction.awayWinProbability() == null) {
            return List.of();
        }

        BigDecimal home = prediction.homeWinProbability();
        BigDecimal draw = prediction.drawProbability();
        BigDecimal away = prediction.awayWinProbability();

        if (!validProbability(home) || !validProbability(draw) || !validProbability(away)) {
            return List.of();
        }

        BigDecimal sum = home.add(draw).add(away);
        if (sum.compareTo(new BigDecimal("0.98")) < 0
                || sum.compareTo(new BigDecimal("1.02")) > 0) {
            return List.of();
        }

        return List.of(
                new Probability("Home win", toPercentage(home)),
                new Probability("Draw", toPercentage(draw)),
                new Probability("Away win", toPercentage(away))
        );
    }

    private static BigDecimal toPercentage(BigDecimal probability) {
        return probability.multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private static boolean validProbability(BigDecimal value) {
        return value != null
                && value.compareTo(BigDecimal.ZERO) >= 0
                && value.compareTo(BigDecimal.ONE) <= 0;
    }

    private static String resolvePhase(String status, boolean postMatchAvailable) {
        if (postMatchAvailable || "FINISHED".equalsIgnoreCase(status)) {
            return "POST_MATCH";
        }
        if ("LIVE".equalsIgnoreCase(status)) {
            return "LIVE";
        }
        if ("SCHEDULED".equalsIgnoreCase(status)) {
            return "PRE_MATCH";
        }
        return "UNKNOWN";
    }

    private static List<String> hashtagList(String home, String away, String competition) {
        List<String> values = new ArrayList<>(List.of(
                "#Football",
                "#FootballAnalysis",
                "#TacticalAnalysis"
        ));

        addHashtag(values, home);
        addHashtag(values, away);
        addHashtag(values, competition);

        return values.stream().distinct().limit(8).toList();
    }

    private static void addHashtag(List<String> hashtags, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        String tag = value.replaceAll("[^\\p{L}\\p{N}]", "");
        if (!tag.isBlank()) {
            hashtags.add("#" + tag);
        }
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1).stripTrailing() + "…";
    }
}