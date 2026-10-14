package com.mmo.module.fb.ingest.provider.sofascore;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.ingest.provider.model.OddsQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaOddsData;
import com.mmo.module.fb.ingest.provider.service.SofaCrawlerService;
import com.mmo.module.fb.ingest.provider.spi.OddsProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SofaScoreOddsProvider extends AbstractSofaScoreProvider implements OddsProvider {

    private static final String AGGREGATED_BOOKMAKER = "SofaScore Aggregated";

    public SofaScoreOddsProvider(SofaCrawlerService sofaCrawlerService) {
        super(sofaCrawlerService);
    }

    @Override
    public List<ProviderOdds> fetchOdds(OddsQuery query) {
        if (query == null || query.fixtureExternalId() == null) return List.of();
        final long eventId;
        try {
            eventId = Long.parseLong(query.fixtureExternalId());
        } catch (NumberFormatException ex) {
            return List.of();
        }

        return withPage(page -> {
            Map<Long, SofaOddsData.MatchOddDetailDTO> allOdds =
                    sofaCrawlerService.fetchDailyMatchOdds(page);
            if (allOdds == null) return List.of();
            SofaOddsData.MatchOddDetailDTO market = allOdds.get(eventId);
            if (market == null || market.getChoices() == null) return List.of();

            String marketName = normalizeMarket(market.getMarketName());
            if (marketName == null) return List.of();
            List<ProviderOdds> result = new ArrayList<>();
            for (SofaOddsData.ChoiceDTO choice : market.getChoices()) {
                if (choice == null) continue;
                String outcome = normalizeOutcome(choice.getName());
                BigDecimal odd = toDecimalOdds(choice.getFractionalValue());
                if (outcome != null && odd != null) {
                    result.add(new ProviderOdds(AGGREGATED_BOOKMAKER, marketName, outcome, odd));
                }
            }
            return result;
        });
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
