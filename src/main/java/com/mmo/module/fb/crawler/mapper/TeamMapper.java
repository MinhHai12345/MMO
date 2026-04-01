package com.mmo.module.fb.crawler.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.mmo.converter.AbstractMapper;
import com.mmo.module.fb.entity.Team;
import org.springframework.stereotype.Component;

@Component
public class TeamMapper extends AbstractMapper<JsonNode, Team> {

    @Override
    public Team map(JsonNode source, Team target) {
        JsonNode teamNode = source.path("team");
        return Team.builder()
                .sofaScoreId(teamNode.path("id").asLong())
                .name(teamNode.path("name").asText())
                .code(teamNode.path("nameCode").asText())
                .shortName(teamNode.path("shortName").asText())
                .slug(teamNode.path("slug").asText())
                .logoUrl(String.format("https://api.sofascore.app/api/v1/team/%d/image", teamNode.path("id").asLong()))
                .build();
    }
}
