package com.mmo.module.fb.ingest.application.identity;

import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.ingest.application.port.out.MmoDataStandardizationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared canonical team identity index used by core ingestion and provider enrichment.
 *
 * <p>Provider names are first converted to MMO canonical display names. Matching then
 * uses the canonical name and a conservative normalized key. A result is usable only
 * when exactly one canonical team matches.</p>
 */
@Component
@RequiredArgsConstructor
public class CanonicalTeamIdentityResolver {
    private final MmoDataStandardizationPort standardizer;

    public String canonicalName(String providerName) {
        return standardizer.teamName(providerName);
    }

    public Map<String, List<Team>> index(Collection<Team> teams) {
        Map<String, List<Team>> byCanonicalName = new HashMap<>();
        Map<String, List<Team>> byMatchKey = new HashMap<>();
        if (teams != null) {
            teams.forEach(team -> addTeam(byCanonicalName, byMatchKey, team));
        }
        return new TeamIdentityIndex(byCanonicalName, byMatchKey).asCombinedIndex();
    }

    /**
     * Adds a team to both identity indexes. The returned index map is not used;
     * this overload exists for callers that maintain the two maps in a cache.
     */
    public void addTeam(Map<String, List<Team>> byCanonicalName,
                        Map<String, List<Team>> byMatchKey,
                        Team team) {
        if (team == null || !StringUtils.hasText(team.getName())) {
            return;
        }

        String canonicalName = canonicalName(team.getName());
        if (!StringUtils.hasText(canonicalName)) {
            return;
        }

        addUnique(byCanonicalName, canonicalName.toLowerCase(Locale.ROOT), team);
        String matchKey = standardizer.teamNameMatchKey(canonicalName);
        if (StringUtils.hasText(matchKey)) {
            addUnique(byMatchKey, matchKey, team);
        }
    }

    public List<Team> findCandidates(String providerName,
                                     Map<String, List<Team>> byCanonicalName,
                                     Map<String, List<Team>> byMatchKey) {
        String canonicalName = canonicalName(providerName);
        if (!StringUtils.hasText(canonicalName)) {
            return List.of();
        }

        List<Team> exact = byCanonicalName.getOrDefault(
                canonicalName.toLowerCase(Locale.ROOT), List.of());
        if (!exact.isEmpty()) {
            return exact;
        }

        String matchKey = standardizer.teamNameMatchKey(canonicalName);
        return StringUtils.hasText(matchKey)
                ? byMatchKey.getOrDefault(matchKey, List.of())
                : List.of();
    }

    public Team resolve(String providerName,
                        Map<String, List<Team>> byCanonicalName,
                        Map<String, List<Team>> byMatchKey) {
        List<Team> candidates = findCandidates(providerName, byCanonicalName, byMatchKey);
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static void addUnique(Map<String, List<Team>> index, String key, Team team) {
        List<Team> matches = index.computeIfAbsent(key, ignored -> new ArrayList<>());
        boolean alreadyIndexed = matches.stream().anyMatch(existing ->
                existing == team || (existing.getId() != null && team.getId() != null
                        && existing.getId().equals(team.getId())));
        if (!alreadyIndexed) {
            matches.add(team);
        }
    }

    /**
     * Convenience wrapper for building indexes in adapters that need a single value.
     * The combined map stores canonical entries under their lower-case names and
     * normalized entries under a prefixed key to prevent key-space collisions.
     */
    private record TeamIdentityIndex(Map<String, List<Team>> canonical,
                                     Map<String, List<Team>> normalized) {
        private Map<String, List<Team>> asCombinedIndex() {
            Map<String, List<Team>> combined = new HashMap<>(canonical);
            normalized.forEach((key, value) -> combined.put("#" + key, value));
            return combined;
        }
    }
}
