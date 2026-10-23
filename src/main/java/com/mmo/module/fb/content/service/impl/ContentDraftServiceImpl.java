package com.mmo.module.fb.content.service.impl;

import com.mmo.module.fb.content.service.ContentDraftService;
import com.mmo.module.fb.content.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.service.ContentDraftService.QualityReview;

import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.ContentPlan;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.MatchSocialInsight;
import com.mmo.module.fb.analytics.service.PostMatchAnalysisService.PostMatchInsight;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentDraftServiceImpl implements ContentDraftService {

    private final MatchSocialAnalyticsService socialAnalyticsService;
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
                opening = "Điều gì có thể quyết định trận " + home + " gặp " + away + "? Hãy bắt đầu từ dữ liệu hiện có.";
                addAll(outline, plan == null ? List.of() : plan.preMatchAngles());
                if (insight.home() != null && insight.home().recentForm() != null) {
                    evidence.add(home + " recent form: " + insight.home().recentForm());
                }
                if (insight.away() != null && insight.away().recentForm() != null) {
                    evidence.add(away + " recent form: " + insight.away().recentForm());
                }
            }
            case POST_MATCH -> {
                title = home + " vs " + away + ": điều gì đã quyết định kết quả?";
                opening = safe(postMatch.resultSummary(), "Trận đấu đã kết thúc; cùng xem lại những dữ kiện được ghi nhận.");
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
                        : "Trước giờ bóng lăn, đây là những dữ liệu và câu hỏi chiến thuật đáng theo dõi.";
                addAll(outline, finished && plan != null ? plan.postMatchAngles()
                        : plan == null ? List.of() : plan.preMatchAngles());
                addAll(outline, plan == null ? List.of() : plan.longVideoAngles());
                if (finished) {
                    evidence.addAll(postMatch.evidenceBasedFindings());
                    caveats.addAll(postMatch.dataWarnings());
                }
            }
            case SHORTS -> {
                title = home + " vs " + away + ": điểm đáng chú ý";
                opening = firstNonBlank(hooks, "Một dữ kiện đáng chú ý trước khi kết luận về trận đấu.");
                addAll(outline, finished && plan != null ? plan.postMatchAngles()
                        : plan == null ? List.of() : plan.preMatchAngles());
                if (finished) {
                    evidence.addAll(postMatch.evidenceBasedFindings());
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported draft format");
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
                outline.stream().distinct().toList(), hooks.stream().distinct().limit(5).toList(),
                evidence.stream().distinct().toList(), caveats.stream().distinct().toList(), null);
        QualityReview review = qualityGate.review(candidate, insight);
        String status = review.decision().equals("BLOCKED") ? "BLOCKED"
                : review.decision().equals("REVIEW_REQUIRED") ? "NEEDS_EDITOR_REVIEW" : "READY_FOR_EDITOR_REVIEW";
        return new ContentDraft(matchId, format, status, title, opening, candidate.outline(),
                candidate.shortHooks(), candidate.evidence(), candidate.caveats(), review);
    }

    private static void addAll(List<String> target, List<String> source) {
        if (source != null) {
            source.stream().filter(value -> value != null && !value.isBlank()).forEach(target::add);
        }
    }

    private static String firstNonBlank(List<String> values, String fallback) {
        return values.stream().filter(value -> value != null && !value.isBlank()).findFirst().orElse(fallback);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
