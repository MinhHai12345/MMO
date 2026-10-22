package com.mmo.module.fb.content.production.impl;

import com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.production.ContentProductionPlanService;
import com.mmo.module.fb.content.production.ContentProductionPlanService.CaptionCue;
import com.mmo.module.fb.content.production.ContentProductionPlanService.MediaCue;
import com.mmo.module.fb.content.production.ContentProductionPlanService.ProductionPlan;
import com.mmo.module.fb.content.production.ContentProductionPlanService.QualityIssue;
import com.mmo.module.fb.content.provenance.ContentClaimProvenance;
import com.mmo.module.fb.content.provenance.ContentClaimProvenance.ClaimType;
import com.mmo.module.fb.content.provenance.ContentClaimProvenance.VerificationStatus;
import com.mmo.module.fb.content.service.SocialContentPackageService;
import com.mmo.module.fb.content.service.SocialContentPackageService.SocialContentPackage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentProductionPlanServiceImpl implements ContentProductionPlanService {

    private final SocialContentPackageService packageService;

    @Override
    public ProductionPlan create(Long matchId, DraftFormat format) {
        SocialContentPackage content = packageService.generate(matchId, format);
        List<ContentClaimProvenance> claims = new ArrayList<>();
        List<QualityIssue> issues = new ArrayList<>();

        for (String evidence : content.evidence()) {
            claims.add(new ContentClaimProvenance(
                    evidence, ClaimType.STATISTICAL, VerificationStatus.UNVERIFIED,
                    List.of(), "Evidence text is present but has not been linked to a source record."));
        }
        for (String point : content.analysisPoints()) {
            claims.add(new ContentClaimProvenance(
                    point, ClaimType.ANALYTICAL, VerificationStatus.UNVERIFIED,
                    List.of(), "Editorial inference; validate against source data before publication."));
        }

        if (claims.isEmpty()) {
            issues.add(new QualityIssue("BLOCKER", "NO_TRACEABLE_CLAIMS",
                    "No evidence or analysis claims are available for source-level review."));
        } else {
            issues.add(new QualityIssue("WARNING", "CLAIMS_UNVERIFIED",
                    "Claims are not source-verified. Attach provider/resource/field provenance or obtain editor approval."));
        }
        if ("BLOCKED".equalsIgnoreCase(content.editorialStatus())) {
            issues.add(new QualityIssue("BLOCKER", "QUALITY_GATE_BLOCKED",
                    "The content quality gate blocked this package; do not publish."));
        }
        if (content.caveats().isEmpty()) {
            issues.add(new QualityIssue("WARNING", "NO_CAVEATS",
                    "No limitations were supplied. Confirm data coverage and uncertainty before publication."));
        }

        List<MediaCue> media = buildMediaCues(content, format);
        List<CaptionCue> captions = buildCaptions(content.shortVideoScript());
        boolean blocked = issues.stream().anyMatch(issue -> "BLOCKER".equals(issue.severity()));
        String readiness = blocked ? "BLOCKED" : "EDITOR_REVIEW_REQUIRED";

        return new ProductionPlan(
                matchId, format, content.editorialStatus(), readiness, claims, media, captions,
                issues, "A human editor must approve factual claims, media rights, captions and platform-specific copy.");
    }

    private static List<MediaCue> buildMediaCues(SocialContentPackage content, DraftFormat format) {
        List<MediaCue> cues = new ArrayList<>();
        cues.add(new MediaCue(1, "HOOK", "Licensed match/stadium footage or original animation",
                content.openingHook(), format == DraftFormat.SHORTS ? 3 : 8));
        int sequence = 2;
        for (String point : content.analysisPoints()) {
            cues.add(new MediaCue(sequence++, "ANALYSIS",
                    "Original tactical board, licensed still, or data visualization",
                    point, format == DraftFormat.SHORTS ? 5 : 12));
        }
        if (content.result() != null && !content.result().isBlank()) {
            cues.add(new MediaCue(sequence, "RESULT",
                    "Score graphic built from verified match data", content.result(), 6));
        }
        return List.copyOf(cues);
    }

    private static List<CaptionCue> buildCaptions(String script) {
        if (script == null || script.isBlank()) {
            return List.of();
        }
        String[] lines = script.split("\\R");
        List<CaptionCue> cues = new ArrayList<>();
        int second = 0;
        for (String line : lines) {
            String text = line.strip();
            if (text.isEmpty()) {
                continue;
            }
            int duration = Math.max(2, Math.min(7, (text.length() + 14) / 15));
            cues.add(new CaptionCue(second, second + duration, text));
            second += duration;
        }
        return List.copyOf(cues);
    }
}
