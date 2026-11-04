package com.mmo.module.fb.match.service;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.match.entity.Bookmaker;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchOddsQuote;
import com.mmo.module.fb.match.entity.OddsHistory;
import com.mmo.module.fb.match.enums.MatchOddsMarketType;
import com.mmo.module.fb.match.enums.MatchOddsPeriod;
import com.mmo.module.fb.match.repository.MatchOddsQuoteRepository;
import com.mmo.module.fb.match.repository.OddsHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Writes provider-specific, normalized odds quotes and their immutable price history. */
@Service
@RequiredArgsConstructor
public class MatchOddsQuotePersistenceService {

    private static final Pattern LINE_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");
    private final MatchOddsQuoteRepository quoteRepository;
    private final OddsHistoryRepository historyRepository;

    @Transactional
    public void upsert(Match match, Bookmaker bookmaker, Provider provider,
                       String market, String outcome, BigDecimal explicitLine, BigDecimal odd) {
        if (match == null || match.getId() == null || bookmaker == null || bookmaker.getId() == null
                || provider == null || !StringUtils.hasText(market) || !StringUtils.hasText(outcome)
                || odd == null || odd.signum() <= 0) {
            return;
        }

        MatchOddsMarketType type = marketType(market);
        MatchOddsPeriod period = marketPeriod(market);
        String normalizedOutcome = normalizeOutcome(outcome);
        BigDecimal line = normalizeLine(explicitLine, outcome);
        Instant now = Instant.now();

        MatchOddsQuote quote = quoteRepository
                .findByMatchIdAndBookmakerIdAndProviderAndMarketTypeAndMarketPeriodAndLineAndOutcome(
                        match.getId(), bookmaker.getId(), provider, type, period, line, normalizedOutcome)
                .orElseGet(() -> MatchOddsQuote.builder()
                        .match(match).bookmaker(bookmaker).provider(provider)
                        .marketType(type).marketPeriod(period).line(line)
                        .outcome(normalizedOutcome).build());

        BigDecimal previous = quote.getOdd();
        if (previous != null && previous.compareTo(odd) == 0) {
            return;
        }

        quote.setOdd(odd);
        quote.setFetchedAt(now);
        quote = quoteRepository.save(quote);

        historyRepository.save(OddsHistory.builder()
                .quote(quote)
                .outcome(normalizedOutcome)
                .line(line)
                .odd(odd)
                .recordedAt(now)
                .build());
    }

    private static MatchOddsMarketType marketType(String market) {
        String value = market.toLowerCase(Locale.ROOT);
        if (value.contains("match winner") || value.contains("1x2") || value.contains("full time result")) {
            return MatchOddsMarketType.MATCH_RESULT;
        }
        if (value.contains("over") || value.contains("under") || value.contains("goals")) {
            return MatchOddsMarketType.OVER_UNDER;
        }
        if (value.contains("both teams") || value.contains("btts")) {
            return MatchOddsMarketType.BOTH_TEAMS_TO_SCORE;
        }
        if (value.contains("handicap")) {
            return MatchOddsMarketType.ASIAN_HANDICAP;
        }
        if (value.contains("double chance")) {
            return MatchOddsMarketType.DOUBLE_CHANCE;
        }
        return MatchOddsMarketType.OTHER;
    }

    private static MatchOddsPeriod marketPeriod(String market) {
        String value = market.toUpperCase(Locale.ROOT);
        if (value.contains("CLOSE") || value.contains("CLOSING")) return MatchOddsPeriod.PRE_MATCH_CLOSE;
        if (value.contains("OPEN") || value.contains("OPENING")) return MatchOddsPeriod.PRE_MATCH_OPEN;
        if (value.contains("LIVE")) return MatchOddsPeriod.LIVE;
        return MatchOddsPeriod.UNKNOWN;
    }

    private static String normalizeOutcome(String outcome) {
        String value = outcome.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
        return switch (value) {
            case "HOME", "1" -> "HOME";
            case "DRAW", "X" -> "DRAW";
            case "AWAY", "2" -> "AWAY";
            case "YES" -> "YES";
            case "NO" -> "NO";
            default -> value.length() > 40 ? value.substring(0, 40) : value;
        };
    }

    private static BigDecimal normalizeLine(BigDecimal explicitLine, String outcome) {
        if (explicitLine != null) return explicitLine.setScale(3, java.math.RoundingMode.HALF_UP);
        Matcher matcher = LINE_PATTERN.matcher(outcome);
        if (matcher.find()) {
            try {
                return new BigDecimal(matcher.group(1)).setScale(3, java.math.RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {
                // A non-numeric outcome uses the canonical zero line.
            }
        }
        return BigDecimal.ZERO.setScale(3);
    }
}
