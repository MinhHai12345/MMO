package com.mmo.module.fb.persistence;

import com.mmo.module.fb.analytics.entity.MatchInsight;
import com.mmo.module.fb.analytics.entity.MatchNarratives;
import com.mmo.module.fb.analytics.entity.MatchPrediction;
import com.mmo.module.fb.analytics.entity.PredictionPerformance;
import com.mmo.module.fb.league.entity.LeagueStanding;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.entity.SeasonExternalId;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchExternalId;
import com.mmo.module.fb.player.entity.Player;
import com.mmo.module.fb.player.entity.PlayerExternalId;
import com.mmo.module.fb.player.entity.PlayerMatchStats;
import com.mmo.module.fb.publish.entity.ContentQueue;
import com.mmo.module.fb.publish.entity.SocialPost;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.team.entity.TeamExternalId;
import com.mmo.module.fb.team.entity.TeamForm;
import com.mmo.module.fb.monetization.entity.AffiliateTracking;
import com.mmo.module.fb.monetization.entity.Subscriber;
import com.mmo.module.fb.monetization.entity.SubscriptionPlan;
import com.mmo.module.fb.monetization.entity.TipTransaction;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FbJpaRelationshipMappingTest {

    @Test
    void matchAndAnalyticsAssociationsRemainConsistentAfterModuleMove() throws Exception {
        assertFieldAnnotation(Match.class, "season", ManyToOne.class);
        assertFieldAnnotation(Match.class, "homeTeam", ManyToOne.class);
        assertFieldAnnotation(Match.class, "awayTeam", ManyToOne.class);
        assertMappedBy(Match.class, "externalIds", "match");

        assertFieldAnnotation(MatchPrediction.class, "match", OneToOne.class);
        assertFieldAnnotation(MatchInsight.class, "match", OneToOne.class);
        assertFieldAnnotation(MatchNarratives.class, "match", OneToOne.class);
        assertOneToOneMappedBy(MatchPrediction.class, "performance", "prediction");
        assertFieldAnnotation(PredictionPerformance.class, "prediction", OneToOne.class);
        assertFieldAnnotation(MatchExternalId.class, "match", ManyToOne.class);
    }

    @Test
    void leagueTeamAndPlayerInverseRelationshipsPointToExistingOwningFields() throws Exception {
        assertFieldAnnotation(Season.class, "league", ManyToOne.class);
        assertMappedBy(Season.class, "externalIds", "season");
        assertFieldAnnotation(SeasonExternalId.class, "season", ManyToOne.class);

        assertFieldAnnotation(LeagueStanding.class, "season", ManyToOne.class);
        assertFieldAnnotation(LeagueStanding.class, "team", ManyToOne.class);

        assertMappedBy(Team.class, "externalIds", "team");
        assertFieldAnnotation(TeamExternalId.class, "team", ManyToOne.class);
        assertFieldAnnotation(TeamForm.class, "team", ManyToOne.class);
        assertFieldAnnotation(TeamForm.class, "season", ManyToOne.class);

        assertMappedBy(Player.class, "externalIds", "player");
        assertMappedBy(Player.class, "matchStats", "player");
        assertFieldAnnotation(PlayerExternalId.class, "player", ManyToOne.class);
        assertFieldAnnotation(PlayerMatchStats.class, "player", ManyToOne.class);
        assertFieldAnnotation(PlayerMatchStats.class, "match", ManyToOne.class);
        assertFieldAnnotation(PlayerMatchStats.class, "team", ManyToOne.class);
    }

    @Test
    void contentPublishingAndMonetizationInverseRelationshipsRemainConsistent() throws Exception {
        assertFieldAnnotation(SocialPost.class, "match", ManyToOne.class);
        assertMappedBy(SocialPost.class, "queue", "socialPost");
        assertFieldAnnotation(ContentQueue.class, "socialPost", ManyToOne.class);

        assertFieldAnnotation(Subscriber.class, "subscriptionPlan", ManyToOne.class);
        assertMappedBy(Subscriber.class, "tipTransactions", "subscriber");
        assertMappedBy(Subscriber.class, "affiliateTrackings", "subscriber");
        assertFieldAnnotation(SubscriptionPlan.class, "subscribers", OneToMany.class);
        assertFieldAnnotation(TipTransaction.class, "subscriber", ManyToOne.class);
        assertFieldAnnotation(AffiliateTracking.class, "subscriber", ManyToOne.class);
    }

    private static void assertFieldAnnotation(Class<?> type, String fieldName,
                                              Class<? extends java.lang.annotation.Annotation> annotationType)
            throws Exception {
        assertNotNull(field(type, fieldName).getAnnotation(annotationType),
                () -> type.getSimpleName() + "." + fieldName + " should declare " + annotationType.getSimpleName());
    }

    private static void assertMappedBy(Class<?> type, String fieldName, String expectedMappedBy) throws Exception {
        OneToMany association = field(type, fieldName).getAnnotation(OneToMany.class);
        assertNotNull(association, () -> type.getSimpleName() + "." + fieldName + " should declare @OneToMany");
        assertEquals(expectedMappedBy, association.mappedBy());
    }

    private static void assertOneToOneMappedBy(Class<?> type, String fieldName, String expectedMappedBy)
            throws Exception {
        OneToOne association = field(type, fieldName).getAnnotation(OneToOne.class);
        assertNotNull(association, () -> type.getSimpleName() + "." + fieldName + " should declare @OneToOne");
        assertEquals(expectedMappedBy, association.mappedBy());
    }

    private static java.lang.reflect.Field field(Class<?> type, String fieldName) throws NoSuchFieldException {
        return type.getDeclaredField(fieldName);
    }
}
