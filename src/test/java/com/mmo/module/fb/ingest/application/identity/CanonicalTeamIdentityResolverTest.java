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
    void doesNotResolveUnrelatedNamesByFuzzySimilarity() {
        Map<String, List<Team>> canonical = new java.util.HashMap<>();
        Map<String, List<Team>> normalized = new java.util.HashMap<>();
        resolver.addTeam(canonical, normalized, team(5L, "Ajaccio"));
        resolver.addTeam(canonical, normalized, team(6L, "Ajaccio GFCO"));

        assertNull(resolver.resolve("AC Ajaccio", canonical, normalized));
    }

    private static Team team(Long id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        return team;
    }
}
