package com.mmo.module.fb.crawler.model.sofa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SofaUniqueTournamentsData {

    @JsonProperty("uniqueTournaments")
    private List<UniqueTournamentDTO> uniqueTournaments;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UniqueTournamentDTO {

        @JsonProperty("id")
        private Long id;

        private String name;
        private String slug;
        private String primaryColorHex;
        private String secondaryColorHex;
        private Long userCount;

        private CategoryDTO category;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CategoryDTO {
        private Long id;
        private String name;
        private String slug;
        private String flag;
        private String alpha2;
        private SportDTO sport;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SportDTO {
        private Integer id;
        private String name;
        private String slug;
    }
}
