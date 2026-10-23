package com.mmo.module.fb.persistence;

import com.mmo.module.fb.analytics.entity.MatchInsight;
import com.mmo.module.fb.analytics.entity.MatchNarratives;
import com.mmo.module.fb.analytics.entity.MatchPrediction;
import com.mmo.module.fb.analytics.entity.PredictionPerformance;
import com.mmo.module.fb.content.entity.ContentDraftRecordEntity;
import com.mmo.module.fb.league.entity.LeagueStanding;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.player.entity.PlayerMatchStats;
import com.mmo.module.fb.publish.entity.ContentQueue;
import com.mmo.module.fb.publish.entity.SocialPost;
import com.mmo.module.fb.team.entity.TeamForm;
import com.mmo.module.fb.monetization.entity.AffiliateTracking;
import com.mmo.module.fb.monetization.entity.Subscriber;
import com.mmo.module.fb.monetization.entity.TipTransaction;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class FbJpaRelationshipMappingTest {

    @Test
    void matchAndAnalyticsAssociationsRemainConsistentAfterModuleMove() throws Exception {
        assertFieldAnnotation(Match.class, "season", ManyToOne.class);
        assertFieldAnnotation(Match.class, "homeTeam", ManyToOne.class);
        assertFieldAnnotation(Match.class, "awayTeam", ManyToOne.class);
        assertFieldAnnotation(MatchPrediction.class, "match", OneToOne.class);
        assertFieldAnnotation(MatchInsight.class, "match", OneToOne.class);
        assertFieldAnnotation(MatchNarratives.class, "match", OneToOne.class);

        OneToOne inversePerformance = field(MatchPrediction.class, "performance").getAnnotation(OneToOne.class);
        assertNotNull(inversePerformance);
        assertEquals("prediction", inversePerformance.mappedBy());

        OneToOne owningPerformance = field(PredictionPerformance.class, "prediction").getAnnotation(OneToOne.class);
        assertNotNull(owningPerformance);
        assertEquals("", owningPerformance.mappedBy());
    }

    @Test
    void leagueTeamAndPlayerAssociationsRemainConsistentAfterModuleMove() throws Exception {
        assertFieldAnnotation(Season.class, "league", ManyToOne.class);
        assertFieldAnnotation(LeagueStanding.class, "season", ManyToOne.class);
        assertFieldAnnotation(LeagueStanding.class, "team", ManyToOne.class);
        assertFieldAnnotation(TeamForm.class, "team", ManyToOne.class);
        assertFieldAnnotation(TeamForm.class, "season", ManyToOne.class);
        assertFieldAnnotation(PlayerMatchStats.class, "player", ManyToOne.class);
        assertFieldAnnotation(PlayerMatchStats.class, "match", ManyToOne.class);
        assertFieldAnnotation(PlayerMatchStats.class, "team", ManyToOne.class);
    }

    @Test
    void contentPublishingAndMonetizationAssociationsRemainConsistentAfterModuleMove() throws Exception {
        assertFieldAnnotation(SocialPost.class, "match", ManyToOne.class);
        assertFieldAnnotation(ContentQueue.class, "socialPost", ManyToOne.class);
        assertFieldAnnotation(Subscriber.class, "subscriptionPlan", ManyToOne.class);
        assertFieldAnnotation(TipTransaction.class, "subscriber", ManyToOne.class);
        assertFieldAnnotation(AffiliateTracking.class, "subscriber", ManyToOne.class);
    }

    private static void assertFieldAnnotation(Class<?> type, String fieldName,
                                              Class<? extends java.lang.annotation.Annotation> annotationType)
            throws Exception {
        assertNotNull(field(type, fieldName).getAnnotation(annotationType),
                () -> type.getSimpleName() + "." + fieldName + " should declare " + annotationType.getSimpleName());
    }

    private static Field field(Class<?> type, String fieldName) throws NoSuchFieldException {
        return type.getDeclaredField(fieldName);
    }
}
