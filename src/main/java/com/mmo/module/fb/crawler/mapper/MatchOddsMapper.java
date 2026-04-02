package com.mmo.module.fb.crawler.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.mmo.converter.AbstractMapper;
import com.mmo.module.fb.entity.MatchOdds;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class MatchOddsMapper extends AbstractMapper<JsonNode, MatchOdds> {

    @Override
    public MatchOdds map(JsonNode source, MatchOdds target) {
        JsonNode markets = source.path("markets");

        for (JsonNode market : markets) {
            String marketName = market.path("marketName").asText();

            // Kèo 1X2
            if ("Full time".equalsIgnoreCase(marketName)) {
                JsonNode choices = market.path("choices");
                if (choices.size() >= 3) {
                    target.setHomeOdds(parseFractional(choices.get(0).path("fractionalValue").asText()));
                    target.setDrawOdds(parseFractional(choices.get(1).path("fractionalValue").asText()));
                    target.setAwayOdds(parseFractional(choices.get(2).path("fractionalValue").asText()));
                }
            }

            // Kèo Over/Under 2.5
            if ("Match goals".equalsIgnoreCase(marketName) && "2.5".equals(market.path("choiceGroup").asText())) {
                JsonNode choices = market.path("choices");
                if (choices.size() >= 2) {
                    target.setOverOdds(parseFractional(choices.get(0).path("fractionalValue").asText()));
                    target.setUnderOdds(parseFractional(choices.get(1).path("fractionalValue").asText()));
                }
            }
            // 3. Kèo Asian Handicap (Châu Á)
            if ("Asian handicap".equalsIgnoreCase(marketName)) {
                JsonNode choices = market.path("choices");
                // Thường mốc Handicap chính sẽ nằm ở choiceGroup hoặc description
                // Ở đây ta lấy mốc handicap từ choice đầu tiên (thường là đội Home)
                if (choices.isArray() && choices.size() >= 2) {
                    // SofaScore trả về handicap dưới dạng text như "-0.5" hoặc "+1.25"
                    String handicapValue = choices.get(0).path("name").asText();

                    // Lưu ý: Bạn cần thêm trường handicap vào Entity MatchOdds nếu chưa có
                    target.setHandicap(parseHandicapValue(handicapValue));

                    // Lưu odds tương ứng cho 2 cửa
                     target.setHomeHandicapOdds(parseFractional(choices.get(0).path("fractionalValue").asText()));
                     target.setAwayHandicapOdds(parseFractional(choices.get(1).path("fractionalValue").asText()));
                }
            }
        }
        return target;
    }

    private BigDecimal parseFractional(String fraction) {
        if (StringUtils.isBlank(fraction) || !fraction.contains("/")) return BigDecimal.ZERO;
        String[] parts = fraction.split("/");
        double val = (Double.parseDouble(parts[0]) / Double.parseDouble(parts[1])) + 1;
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP);
    }
    private BigDecimal parseHandicapValue(String handicapStr) {
        if (StringUtils.isBlank(handicapStr)) return BigDecimal.ZERO;
        try {
            // Loại bỏ các ký tự không phải số/dấu chấm/dấu trừ nếu có
            String cleanValue = handicapStr.replaceAll("[^\\d.-]", "");
            return new BigDecimal(cleanValue).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

}
