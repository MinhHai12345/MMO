package com.mmo.module.fb.content.production.impl;

import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.production.ContentProductionPlanService.ProductionPlan;
import com.mmo.module.fb.content.provenance.ContentClaimProvenance.VerificationStatus;
import com.mmo.module.fb.content.service.SocialContentPackageService;
import com.mmo.module.fb.content.service.SocialContentPackageService.SocialContentPackage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContentProductionPlanServiceImplTest {

    @Test
    void createsReviewablePlanAndDoesNotPretendEvidenceIsVerified() {
        SocialContentPackageService packages = mock(SocialContentPackageService.class);
        when(packages.generate(7L, DraftFormat.SHORTS)).thenReturn(new SocialContentPackage(
                7L, "PRE_MATCH", "League", Instant.parse("2026-10-25T17:00:00Z"),
                "Home", "Away", null, DraftFormat.SHORTS, "NEEDS_EDITOR_REVIEW",
                "Match analysis", "What could decide this match?", List.of("Home create more chances"),
                List.of(), "Model estimates are not guarantees.", List.of("Last five matches"),
                List.of("Limited player data"), "IG caption", "FB post", "X post", "YT description",
                "Hook\nAnalysis\nTakeaway", List.of("#Football")));

        var service = new ContentProductionPlanServiceImpl(packages);
        ProductionPlan plan = service.create(7L, DraftFormat.SHORTS);

        assertEquals("EDITOR_REVIEW_REQUIRED", plan.publicationReadiness());
        assertFalse(plan.claims().isEmpty());
        assertTrue(plan.claims().stream().allMatch(c -> c.verificationStatus() == VerificationStatus.UNVERIFIED));
        assertFalse(plan.mediaCues().isEmpty());
        assertFalse(plan.captions().isEmpty());
        verify(packages).generate(7L, DraftFormat.SHORTS);
    }

    @Test
    void rejectsMissingMatchIdOrFormat() {
        var service = new ContentProductionPlanServiceImpl(mock(SocialContentPackageService.class));
        ResponseStatusException missingMatch = assertThrows(ResponseStatusException.class,
                () -> service.create(null, DraftFormat.PRE_MATCH));
        ResponseStatusException missingFormat = assertThrows(ResponseStatusException.class,
                () -> service.create(1L, null));
        assertEquals(HttpStatus.BAD_REQUEST, missingMatch.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, missingFormat.getStatusCode());
    }
}
