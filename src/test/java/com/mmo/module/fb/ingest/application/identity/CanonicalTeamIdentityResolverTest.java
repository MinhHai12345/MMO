package com.mmo.module.fb.ingest.application.identity;

import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.ingest.adapter.out.provider.standardization.MmoDataStandardizationAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CanonicalTeamIdentityResolverTest {
    private CanonicalTeamIdentityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CanonicalTeamIdentityResolver(new MmoDataStandardizationAdapter());
    }

    @Test
    void resolvesProviderAliasAfterCanonicalizingItToMmoName() {
        Team bolton = team(1L, "Bolton Wanderers");
        Map<String, List<Team>> canonical = new java.util.HashMap<>();
        Map<String, List<Team>> normalized = new java.util.HashMap<>();
        resolver.addTeam(canonical, normalized, bolton);

        assertEquals("Bolton Wanderers", resolver.canonicalName("Bolton"));
        assertSame(bolton, resolver.resolve("Bolton", canonical, normalized));
        assertSame(bolton, resolver.resolve("Bolton Wanderers FC", canonical, normalized));
    }

    @Test
    void canonicalizesStoredDatabaseNamesBeforeIndexing() {
        Team storedWithProviderSuffix = team(2L, "Lincoln City FC");
        Map<String, List<Team>> canonical = new java.util.HashMap<>();
        Map<String, List<Team>> normalized = new java.util.HashMap<>();
        resolver.addTeam(canonical, normalized, storedWithProviderSuffix);

        assertEquals("Lincoln City", resolver.canonicalName("Lincoln"));
        assertSame(storedWithProviderSuffix, resolver.resolve("Lincoln", canonical, normalized));
    }

    @Test
    void rejectsAmbiguousCanonicalIdentity() {
        Map<String, List<Team>> canonical = new java.util.HashMap<>();
        Map<String, List<Team>> normalized = new java.util.HashMap<>();
        resolver.addTeam(canonical, normalized, team(3L, "Bolton Wanderers"));
        resolver.addTeam(canonical, normalized, team(4L, "Bolton Wanderers FC"));

        assertNull(resolver.resolve("Bolton", canonical, normalized));
        assertEquals(2, resolver.findCandidates("Bolton", canonical, normalized).size());
    }

    @Test
    void resolvesFootballDataUkAliasesToExistingCanonicalTeams() {
        Map<String, List<Team>> canonical = new java.util.HashMap<>();
        Map<String, List<Team>> normalized = new java.util.HashMap<>();
        List<Team> teams = List.of(
                team(10L, "Bolton Wanderers"),
                team(11L, "Preston North End"),
                team(12L, "Lincoln City"),
                team(13L, "Middlesbrough"),
                team(14L, "Portsmouth"),
                team(15L, "Queens Park Rangers"),
                team(16L, "Blackburn Rovers"),
                team(17L, "Sheffield United"),
                team(18L, "Southampton"),
                team(19L, "Millwall"),
                team(20L, "West Ham")
        );
        teams.forEach(team -> resolver.addTeam(canonical, normalized, team));

        Map<String, Long> expectedIds = Map.of(
                "Bolton", 10L,
                "Preston", 11L,
                "Lincoln", 12L,
                "Middlesbrough FC", 13L,
                "Portsmouth FC", 14L,
                "QPR", 15L,
                "Blackburn", 16L,
                "Sheffield United FC", 17L,
                "Southampton FC", 18L,
                "Millwall FC", 19L,
                "West Ham United FC", 20L
        );
        expectedIds.forEach((providerName, expectedId) ->
                assertEquals(expectedId, resolver.resolve(providerName, canonical, normalized).getId(),
                        providerName));
    }

    @Test
    void doesNotResolveUnrelatedNamesByFuzzySimilarity() {
        Map<String, List<Team>> canonical = new java.util.HashMap<>();
        Map<String, List<Team>> normalized = new java.util.HashMap<>();
        resolver.addTeam(canonical, normalized, team(5L, "Ajaccio"));
        resolver.addTeam(canonical, normalized, team(6L, "Ajaccio GFCO"));

        assertNull(resolver.resolve("Ajaccio City", canonical, normalized));
    }

    private static Team team(Long id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        return team;
    }
}
