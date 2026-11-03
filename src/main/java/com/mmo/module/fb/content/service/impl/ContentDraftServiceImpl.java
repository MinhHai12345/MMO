package com.mmo.module.fb.content.service.impl;

import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.ContentPlan;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.MatchSocialInsight;
import com.mmo.module.fb.analytics.service.PostMatchAnalysisService.PostMatchInsight;
import com.mmo.module.fb.analytics.service.PreMatchAnalysisService;
import com.mmo.module.fb.analytics.service.PreMatchAnalysisService.GoalModel;
import com.mmo.module.fb.analytics.service.PreMatchAnalysisService.PreMatchReport;
import com.mmo.module.fb.analytics.service.PreMatchAnalysisService.TeamForm;
import com.mmo.module.fb.content.service.ContentDraftService;
import com.mmo.module.fb.content.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.service.ContentDraftService.QualityReview;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentDraftServiceImpl implements ContentDraftService {

    private final MatchSocialAnalyticsService socialAnalyticsService;
    private final PreMatchAnalysisService preMatchAnalysisService;
    private final ContentDraftQualityGate qualityGate;

    @Override
    public ContentDraft generate(Long matchId, DraftFormat format) {
        if (matchId == null || format == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "matchId and format are required");
        }

        MatchSocialInsight insight = socialAnalyticsService.analyze(matchId);
        ContentPlan plan = insight.contentPlan();
        PostMatchInsight postMatch = insight.postMatchAnalysis();
        if (format == DraftFormat.POST_MATCH && postMatch == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Post-match draft requires a finished match and available post-match analysis");
        }

        String home = safe(insight.home() == null ? null : insight.home().name(), "Home team");
        String away = safe(insight.away() == null ? null : insight.away().name(), "Away team");
        boolean finished = postMatch != null;
        boolean preMatchExpected = !finished && format != DraftFormat.POST_MATCH;
        PreMatchReport preMatch = null;
        String preMatchFailure = null;
        if (preMatchExpected) {
            try {
                preMatch = preMatchAnalysisService.analyze(matchId);
            } catch (RuntimeException ex) {
                // Content generation should still produce an honest draft from other
                // available analytics; the quality gate will force editorial review.
                preMatchFailure = ex instanceof ResponseStatusException response
                        && response.getReason() != null
                        ? response.getReason() : "Pre-match analysis could not be generated.";
            }
        }

        List<String> outline = new ArrayList<>();
        List<String> hooks = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        List<String> caveats = new ArrayList<>();

        if (plan != null) {
            caveats.addAll(plan.factualCaveats());
            hooks.addAll(plan.shortsHooks());
        }
        caveats.addAll(insight.dataWarnings());

        String title;
        String opening;
        switch (format) {
            case PRE_MATCH -> {
                title = home + " vs " + away + ": những yếu tố đáng theo dõi";
                opening = "Điều gì có thể quyết định trận " + home + " gặp " + away
                        + "? Hãy bắt đầu từ phong độ, dữ liệu bàn thắng và những giới hạn của mẫu thống kê.";
                addAll(outline, plan == null ? List.of() : plan.preMatchAngles());
                addSocialFormEvidence(evidence, home, away, insight);
            }
            case POST_MATCH -> {
                title = home + " vs " + away + ": điều gì đã quyết định kết quả?";
                opening = safe(postMatch.resultSummary(),
                        "Trận đấu đã kết thúc; cùng xem lại những dữ kiện được ghi nhận.");
                addAll(outline, postMatch.editorialAngles());
                addAll(outline, postMatch.evidenceBasedFindings());
                evidence.addAll(postMatch.evidenceBasedFindings());
                caveats.addAll(postMatch.dataWarnings());
            }
            case LONG_VIDEO -> {
                title = finished ? home + " vs " + away + ": phân tích sau trận"
                        : home + " vs " + away + ": phân tích trước trận";
                opening = finished
                        ? safe(postMatch.resultSummary(), "Cùng phân tích các dữ kiện sau trận.")
                        : "Trước giờ bóng lăn, hãy nhìn vào phong độ, hiệu suất sân nhà/sân khách "
                                + "và các mô hình bàn thắng thay vì chỉ dựa vào cảm tính.";
                addAll(outline, finished && plan != null ? plan.postMatchAngles()
                        : plan == null ? List.of() : plan.preMatchAngles());
                addAll(outline, plan == null ? List.of() : plan.longVideoAngles());
                if (finished) {
                    evidence.addAll(postMatch.evidenceBasedFindings());
                    caveats.addAll(postMatch.dataWarnings());
                } else {
                    addSocialFormEvidence(evidence, home, away, insight);
                }
            }
            case SHORTS -> {
                title = home + " vs " + away + ": điểm đáng chú ý";
                opening = firstNonBlank(hooks,
                        "Một dữ kiện đáng chú ý trước khi kết luận về trận đấu.");
                addAll(outline, finished && plan != null ? plan.postMatchAngles()
                        : plan == null ? List.of() : plan.preMatchAngles());
                if (finished) {
                    evidence.addAll(postMatch.evidenceBasedFindings());
                } else {
                    addSocialFormEvidence(evidence, home, away, insight);
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported draft format");
        }

        if (preMatch != null) {
            addPreMatchAnalysis(preMatch, home, away, outline, hooks, evidence, caveats);
        } else if (preMatchExpected) {
            caveats.add("PRE_MATCH_ANALYSIS_UNAVAILABLE: " + safe(preMatchFailure,
                    "No pre-match model report is available; do not state model probabilities or expected goals."));
        }

        if (outline.isEmpty()) {
            outline.add("Giới thiệu bối cảnh trận đấu và xác nhận dữ liệu hiện có.");
            outline.add("Trình bày các thông tin đã được xác minh; không suy diễn khi thiếu dữ liệu.");
        }
        if (hooks.isEmpty()) {
            hooks.add(opening);
        }
        if (evidence.isEmpty()) {
            evidence.add("No additional evidence was available from the current analytics payload.");
        }

        ContentDraft candidate = new ContentDraft(matchId, format, "DRAFT", title, opening,
                outline.stream().filter(ContentDraftServiceImpl::notBlank).distinct().toList(),
                hooks.stream().filter(ContentDraftServiceImpl::notBlank).distinct().limit(5).toList(),
                evidence.stream().filter(ContentDraftServiceImpl::notBlank).distinct().toList(),
                caveats.stream().filter(ContentDraftServiceImpl::notBlank).distinct().toList(), null);

        QualityReview review = qualityGate.review(candidate, insight, preMatch, preMatchExpected);
        String status = review.decision().equals("BLOCKED") ? "BLOCKED"
                : review.decision().equals("REVIEW_REQUIRED") ? "NEEDS_EDITOR_REVIEW"
                : "READY_FOR_EDITOR_REVIEW";
        return new ContentDraft(matchId, format, status, title, opening, candidate.outline(),
                candidate.shortHooks(), candidate.evidence(), candidate.caveats(), review);
    }

    private static void addSocialFormEvidence(List<String> evidence, String home, String away,
                                              MatchSocialInsight insight) {
        if (insight.home() != null && insight.home().recentForm() != null) {
            evidence.add(home + " recent form: " + insight.home().recentForm());
        }
        if (insight.away() != null && insight.away().recentForm() != null) {
            evidence.add(away + " recent form: " + insight.away().recentForm());
        }
    }

    private static void addPreMatchAnalysis(PreMatchReport report, String home, String away,
                                            List<String> outline, List<String> hooks,
                                            List<String> evidence, List<String> caveats) {
        caveats.addAll(report.dataWarnings());
        caveats.addAll(report.analysisNotes());
        caveats.add("Chất lượng dữ liệu phân tích trước trận: " + report.dataQuality()
                + ". Các xác suất là ước lượng Poisson, chưa được hiệu chỉnh như xác suất thực tế.");

        addTeamFormEvidence(evidence, home, "sân nhà", report.home());
        addTeamFormEvidence(evidence, away, "sân khách", report.away());

        if (report.headToHead() != null && report.headToHead().matchesUsed() > 0) {
            evidence.add("Đối đầu: " + report.headToHead().summary()
                    + " Trung bình " + decimal(report.headToHead().averageGoals())
                    + " bàn/trận trong " + report.headToHead().matchesUsed() + " lần gặp được ghi nhận.");
            outline.add("Đối chiếu lịch sử đối đầu như dữ liệu bối cảnh, không xem đây là yếu tố quyết định.");
        }

        addGoalModelEvidence(evidence, "Mô hình bàn thắng", report.goalModel());
        addGoalModelEvidence(evidence, "Mô hình xG", report.xgModel());

        if (report.home() != null && report.away() != null) {
            String hook = "Phong độ gần đây của " + home + " và " + away
                    + " nói gì về trận đấu này? Hãy so sánh hiệu suất ghi bàn và phòng ngự trước khi đưa ra nhận định.";
            hooks.add(0, hook);
            outline.add("So sánh phong độ gần đây và hiệu suất sân nhà/sân khách của hai đội.");
        }
        if (report.goalModel() != null || report.xgModel() != null) {
            outline.add("Đặt ước lượng bàn thắng từ dữ liệu thực tế cạnh mô hình xG; nêu rõ khi hai mô hình khác nhau.");
        }
        if (!"GOOD".equalsIgnoreCase(report.dataQuality())) {
            outline.add("Nêu rõ giới hạn mẫu thống kê và tránh kết luận chắc chắn khi dữ liệu chưa đầy đủ.");
        }
    }

    private static void addTeamFormEvidence(List<String> evidence, String teamName,
                                            String venueLabel, TeamForm form) {
        if (form == null) {
            evidence.add(teamName + ": chưa đủ dữ liệu phong độ để tổng hợp.");
            return;
        }
        evidence.add(teamName + ": " + form.matchesUsed() + " trận gần nhất, phong độ "
                + safe(form.recentForm(), "chưa có chuỗi kết quả")
                + ", ghi " + decimal(form.averageGoalsFor()) + " bàn/trận, thủng lưới "
                + decimal(form.averageGoalsAgainst()) + " bàn/trận, trung bình "
                + decimal(form.averagePoints()) + " điểm/trận.");
        if (form.venueMatchesUsed() > 0) {
            evidence.add(teamName + " (" + venueLabel + "): "
                    + form.venueMatchesUsed() + " trận, ghi "
                    + decimal(form.venueAverageGoalsFor()) + " bàn/trận và thủng lưới "
                    + decimal(form.venueAverageGoalsAgainst()) + " bàn/trận.");
        }
        if (form.averageXgFor() != null && form.averageXgAgainst() != null) {
            evidence.add(teamName + " xG gần đây: tạo "
                    + decimal(form.averageXgFor()) + " xG và cho đối thủ "
                    + decimal(form.averageXgAgainst()) + " xG/trận.");
        }
    }

    private static void addGoalModelEvidence(List<String> evidence, String label, GoalModel model) {
        if (model == null) {
            return;
        }
        evidence.add(label + ": kỳ vọng " + decimal(model.expectedHomeGoals())
                + " bàn cho đội chủ nhà và " + decimal(model.expectedAwayGoals())
                + " bàn cho đội khách; xác suất mô hình 1X2 lần lượt "
                + percent(model.homeWinProbability()) + "% / "
                + percent(model.drawProbability()) + "% / "
                + percent(model.awayWinProbability()) + "% (mẫu "
                + model.homeSamples() + "/" + model.awaySamples() + ").");
    }

    private static String percent(double value) {
        return decimal(value * 100.0);
    }

    private static String decimal(Double value) {
        return value == null ? "N/A" : decimal(value.doubleValue());
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void addAll(List<String> target, List<String> source) {
        if (source != null) {
            source.stream().filter(ContentDraftServiceImpl::notBlank).forEach(target::add);
        }
    }

    private static String firstNonBlank(List<String> values, String fallback) {
        return values.stream().filter(ContentDraftServiceImpl::notBlank).findFirst().orElse(fallback);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
