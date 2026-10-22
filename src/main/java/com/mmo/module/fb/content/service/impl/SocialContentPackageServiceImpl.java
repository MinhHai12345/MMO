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
import com.mmo.module.fb.content.service.impl.ContentDraftQualityGate;
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
            "Probabilities are model estimates, not guarantees. They are not betting advice. " +
            "Actual outcomes may differ; check the data timestamp and limitations before publication.";
    private static final DateTimeFormatter KICKOFF_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'").withLocale(Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final MatchSocialAnalyticsService analyticsService;
    private final ContentDraftService draftService;
    private final ContentDraftQualityGate qualityGate;

    @Override
    public SocialContentPackage generate(Long matchId, DraftFormat format) {
        if (matchId == null || matchId <= 0 || format == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive matchId and format are required");
        }

        MatchSocialInsight insight = analyticsService.analyze(matchId);
        ContentDraft draft = qualityGate.evaluate(draftService.generate(matchId, format));
        String phase = resolvePhase(insight.status(), insight.postMatchAnalysis() != null);
        String home = safe(insight.home() == null ? null : insight.home().name(), "Home team");
        String away = safe(insight.away() == null ? null : insight.away().name(), "Away team");
        String fixture = home + " vs " + away;
        String kickoff = insight.kickoff() == null ? null : KICKOFF_FORMAT.format(insight.kickoff());
        String result = insight.postMatchAnalysis() == null ? null : insight.postMatchAnalysis().resultSummary();

        List<Probability> probabilities = "PRE_MATCH".equals(phase) || "LIVE".equals(phase)\n                ? probabilities(insight.prediction()) : List.of();
        List<String> points = Stream.concat(
                insight.editorialAngles().stream(),
                insight.postMatchAnalysis() == null ? Stream.empty()
                        : insight.postMatchAnalysis().evidenceBasedFindings().stream())
                .filter(value -> value != null && !value.isBlank())
                .distinct().limit(8).toList();

        List<String> caveats = Stream.concat(draft.caveats().stream(), insight.dataWarnings().stream())
                .filter(value -> value != null && !value.isBlank()).distinct().toList();

        String probabilityLine = probabilities.isEmpty() ? null : probabilities.stream()
                .map(p -> p.outcome() + " " + p.percentage().toPlainString() + "%")
                .collect(Collectors.joining(" | "));
        String title = safe(draft.title(), fixture + ": football analysis");
        String opening = safe(draft.openingHook(), "A data-led look at " + fixture + ".");
        String analysis = points.isEmpty()
                ? "The available dataset does not support a stronger conclusion yet."
                : points.stream().limit(4).map(p -> "• " + p).collect(Collectors.joining("\n"));
        String probabilitySection = probabilityLine == null ? ""
                : "\nModel 1X2 estimates: " + probabilityLine + ".";
        String context = (insight.competition() == null ? "" : insight.competition() + " · ")
                + (kickoff == null ? "" : kickoff);
        String evidenceLine = draft.evidence().isEmpty() ? ""
                : "\nData notes: " + String.join("; ", draft.evidence().stream().limit(3).toList()) + ".";
        String caveatLine = caveats.isEmpty() ? ""
                : "\nLimitations: " + String.join("; ", caveats.stream().limit(2).toList()) + ".";
        String base = title + "\n\n" + opening + "\n\n" + analysis + probabilitySection
                + evidenceLine + caveatLine + "\n\n" + DISCLAIMER;
        String hashtags = hashtags(home, away, insight.competition());

        String shortScript = opening + "\n\n"
                + points.stream().limit(3).map(p -> p + ".").collect(Collectors.joining("\n"))
                + (probabilityLine == null ? "" : "\nModel estimates: " + probabilityLine + ".")
                + "\n\n" + DISCLAIMER;
        String status = draft.status();
        if ("BLOCKED".equals(status)) {
            // Return the diagnostic package for internal review, but make it explicit that it is not publishable.
            caveats = new ArrayList<>(caveats);
            caveats.add(0, "PUBLICATION BLOCKED: resolve all quality blockers before posting.");
        }

        return new SocialContentPackage(matchId, phase, context, insight.kickoff(), home, away, result,
                effectiveFormat, status, title, opening, points, probabilities, DISCLAIMER, draft.evidence(), caveats,
                base + "\n\n" + hashtags, base + "\n\n" + hashtags,
                truncate(opening + " " + String.join(" ", points) + probabilitySection + " " + hashtags, 275),
                base + "\n\n" + hashtags, shortScript, hashtagList(home, away, insight.competition()));
    }

    private static List<Probability> probabilities(PredictionInsight prediction) {
        if (prediction == null || prediction.homeWinProbability() == null
                || prediction.drawProbability() == null || prediction.awayWinProbability() == null) {
            return List.of();
        }
        BigDecimal home = prediction.homeWinProbability();
        BigDecimal draw = prediction.drawProbability();
        BigDecimal away = prediction.awayWinProbability();
        if (!validProbability(home) || !validProbability(draw) || !validProbability(away)) return List.of();
        BigDecimal sum = home.add(draw).add(away);
        if (sum.compareTo(new BigDecimal("0.98")) < 0 || sum.compareTo(new BigDecimal("1.02")) > 0) {
            return List.of();
        }
        return List.of(
                new Probability("Home win", home.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)),
                new Probability("Draw", draw.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)),
                new Probability("Away win", away.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)));
    }

    private static boolean validProbability(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0
                && value.compareTo(BigDecimal.ONE) <= 0;
    }

    private static String resolvePhase(String status, boolean postMatchAvailable) {
        if (postMatchAvailable || "FINISHED".equalsIgnoreCase(status)) return "POST_MATCH";
        if ("LIVE".equalsIgnoreCase(status)) return "LIVE";
        if ("SCHEDULED".equalsIgnoreCase(status)) return "PRE_MATCH";
        return "UNKNOWN";
    }

    private static List<String> hashtagList(String home, String away, String competition) {
        List<String> values = new ArrayList<>();
        values.add("#Football");
        values.add("#FootballAnalysis");
        values.add("#TacticalAnalysis");
        addTeamTag(values, home);
        addTeamTag(values, away);
        if (competition != null && !competition.isBlank()) addTeamTag(values, competition);
        return values.stream().distinct().limit(8).toList();
    }

    private static String hashtags(String home, String away, String competition) {
        return String.join(" ", hashtagList(home, away, competition));
    }

    private static void addTeamTag(List<String> target, String value) {
        if (value == null) return;
        String tag = value.replaceAll("[^\\p{L}\\p{N}]", "");
        if (!tag.isBlank()) target.add("#" + tag);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 1).stripTrailing() + "…";
    }
}
