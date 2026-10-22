package com.mmo.module.fb.analytics.service;

import com.mmo.module.fb.analytics.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.analytics.service.ContentDraftService.QualityReview;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.MatchSocialInsight;

public interface EditorialQualityGateService {

    QualityReview review(ContentDraft draft, MatchSocialInsight insight);
}
