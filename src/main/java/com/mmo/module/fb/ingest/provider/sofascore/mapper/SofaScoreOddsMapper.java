package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.model.ProviderOdds;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaOddsData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class SofaScoreOddsMapper {

    private static final String AGGREGATED_SOURCE = "SofaScore Aggregated";

    public List<ProviderOdds> toProviderOdds(SofaOddsData.MatchOddDetailDTO market) {
        if (market == null || market.getChoices() == null) return List.of();
        String marketName = normalizeMarket(market.getMarketName());
        if (marketName == null) return List.of();
        return market.getChoices().stream()
                .filter(Objects::nonNull)
                .map(choice -> {
                    String outcome = normalizeOutcome(choice.getName());
                    BigDecimal odds = toDecimalOdds(choice.getFractionalValue());
                    return outcome == null || odds == null ? null
                            : new ProviderOdds(AGGREGATED_SOURCE, marketName, outcome, odds);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private String normalizeMarket(String market) {
        if (market == null) return null;
        String normalized = market.toLowerCase(Locale.ROOT);
        if (normalized.contains("full time") || normalized.contains("match winner")
                || normalized.equals("1x2")) return "Match Winner";
        if (normalized.contains("over/under") || normalized.contains("goals")) return "Goals Over/Under";
        if (normalized.contains("both teams")) return "Both Teams Score";
        return null;
    }

    private String normalizeOutcome(String outcome) {
        if (outcome == null) return null;
        return switch (outcome.trim().toLowerCase(Locale.ROOT)) {
            case "1", "home", "team 1" -> "Home";
            case "x", "draw" -> "Draw";
            case "2", "away", "team 2" -> "Away";
            case "over 2.5", "over 2,5" -> "Over 2.5";
            case "under 2.5", "under 2,5" -> "Under 2.5";
            case "yes" -> "Yes";
            case "no" -> "No";
            default -> null;
        };
    }

    private BigDecimal toDecimalOdds(String fractional) {
        if (fractional == null || fractional.isBlank()) return null;
        try {
            String value = fractional.trim();
            if (value.contains("/")) {
                String[] parts = value.split("/");
                if (parts.length != 2) return null;
                BigDecimal numerator = new BigDecimal(parts[0].trim());
                BigDecimal denominator = new BigDecimal(parts[1].trim());
                if (denominator.signum() == 0) return null;
                return numerator.divide(denominator, 4, RoundingMode.HALF_UP)
                        .add(BigDecimal.ONE).setScale(3, RoundingMode.HALF_UP);
            }
            return new BigDecimal(value).setScale(3, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
