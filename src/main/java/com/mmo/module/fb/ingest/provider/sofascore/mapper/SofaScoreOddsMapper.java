package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.*;

import com.mmo.module.fb.ingest.provider.model.MmoProviderDataStandardizer;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class SofaScoreOddsMapper {

    private static final String AGGREGATED_SOURCE = "SofaScore Aggregated";

    public List<ProviderOdds> toProviderOdds(MatchOddDetailDTO market) {
        if (market == null || market.choices() == null) return List.of();
        String marketName = normalizeMarket(market.marketName());
        if (marketName == null) return List.of();
        return market.choices().stream()
                .filter(Objects::nonNull)
                .map(choice -> {
                    String outcome = normalizeOutcome(choice.name());
                    BigDecimal odds = toDecimalOdds(choice.fractionalValue());
                    return outcome == null || odds == null ? null
                            : new ProviderOdds(MmoProviderDataStandardizer.bookmakerName(AGGREGATED_SOURCE), marketName, outcome, odds);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private String normalizeMarket(String market) {
        String normalized = MmoProviderDataStandardizer.displayText(market);
        if (normalized == null) return null;
        normalized = normalized.toLowerCase(Locale.ROOT);
        String standardized = MmoProviderDataStandardizer.oddsMarketName(normalized);
        if ("Match Winner".equals(standardized) || "Goals Over/Under".equals(standardized)
                || "Both Teams Score".equals(standardized)) return standardized;
        return null;
    }

    private String normalizeOutcome(String outcome) {
        String standardized = MmoProviderDataStandardizer.oddsOutcomeName(outcome);
        return switch (standardized == null ? "" : standardized) {
            case "Home", "Draw", "Away", "Over 2.5", "Under 2.5", "Yes", "No" -> standardized;
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
