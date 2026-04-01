package com.mmo.module.fb.crawler.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.mmo.converter.AbstractMapper;
import com.mmo.module.fb.entity.MatchOdds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchOddsMapper extends AbstractMapper<JsonNode, MatchOdds> {

    @Override
    public MatchOdds map(JsonNode source, MatchOdds target) {
        JsonNode choices = source.get("markets").get(0).get("choices");
        System.out.println(String.format("   💰 MatchOdds: 1[%s] X[%s] 2[%s]",
                choices.get(0).get("fractionalValue").asText(),
                choices.get(1).get("fractionalValue").asText(),
                choices.get(2).get("fractionalValue").asText()));
        return null;
    }
}
