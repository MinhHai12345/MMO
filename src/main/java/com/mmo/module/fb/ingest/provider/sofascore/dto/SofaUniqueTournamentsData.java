package com.mmo.module.fb.ingest.provider.sofascore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SofaUniqueTournamentsData(
        @JsonProperty("uniqueTournaments") List<UniqueTournamentDTO> uniqueTournaments
) {
    public List<UniqueTournamentDTO> getUniqueTournaments() { return uniqueTournaments; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UniqueTournamentDTO(@JsonProperty("id") Long id, String name, String slug,
                                      String primaryColorHex, String secondaryColorHex,
                                      Long userCount, CategoryDTO category) {
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getSlug() { return slug; }
        public String getPrimaryColorHex() { return primaryColorHex; }
        public String getSecondaryColorHex() { return secondaryColorHex; }
        public Long getUserCount() { return userCount; }
        public CategoryDTO getCategory() { return category; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryDTO(Long id, String name, String slug, String flag, String alpha2, SportDTO sport) {
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getSlug() { return slug; }
        public String getFlag() { return flag; }
        public String getAlpha2() { return alpha2; }
        public SportDTO getSport() { return sport; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SportDTO(Integer id, String name, String slug) {
        public Integer getId() { return id; }
        public String getName() { return name; }
        public String getSlug() { return slug; }
    }
}
