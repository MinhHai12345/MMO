package com.mmo.module.fb.domain.match.service;

import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.entity.Bookmaker;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchOddsQuote;
import com.mmo.module.fb.domain.match.entity.OddsHistory;
import com.mmo.module.fb.domain.match.enums.MatchOddsMarketType;
import com.mmo.module.fb.domain.match.enums.MatchOddsPeriod;
import com.mmo.module.fb.domain.match.repository.MatchOddsQuoteRepository;
import com.mmo.module.fb.domain.match.repository.OddsHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Writes provider-specific, normalized odds quotes and their immutable price history. */
@Service
@RequiredArgsConstructor
public class MatchOddsQuotePersistenceService {

    private static final Pattern LINE_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");
    private final MatchOddsQuoteRepository quoteRepository;
    private final OddsHistoryRepository historyRepository;

    @Transactional
    public void upsert(Match match, Bookmaker bookmaker, MatchProvider provider,
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
            quote.setFetchedAt(now);
            quoteRepository.save(quote);
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


    /** Bulk quote merge for one match/provider snapshot. */
    @Transactional
    public void upsertAll(Match match, MatchProvider provider,
                          Map<Bookmaker, List<QuoteInput>> valuesByBookmaker) {
        upsertAll(List.of(new QuoteBatchInput(match, provider, valuesByBookmaker)));
    }

    /**
     * Bulk quote merge across a fixture chunk. Existing quote identities are prefetched
     * once per provider, then quote and price-history rows are saved in two batches.
     */
    @Transactional
    public void upsertAll(List<QuoteBatchInput> batches) {
        if (batches == null || batches.isEmpty()) return;
        Map<MatchProvider, List<QuoteBatchInput>> byProvider = new LinkedHashMap<>();
        for (QuoteBatchInput batch : batches) {
            if (batch == null || batch.match() == null || batch.match().getId() == null
                    || batch.provider() == null || batch.valuesByBookmaker() == null
                    || batch.valuesByBookmaker().isEmpty()) continue;
            byProvider.computeIfAbsent(batch.provider(), ignored -> new ArrayList<>()).add(batch);
        }
        byProvider.forEach(this::upsertProviderBatch);
    }

    private void upsertProviderBatch(MatchProvider provider, List<QuoteBatchInput> batches) {
        List<Long> matchIds = batches.stream().map(batch -> batch.match().getId()).distinct().toList();
        Map<QuoteIdentity, MatchOddsQuote> existing = new LinkedHashMap<>();
        quoteRepository.findAllByMatchIdInAndProvider(matchIds, provider).forEach(quote ->
                existing.put(identity(quote.getMatch().getId(), quote.getBookmaker().getId(),
                        quote.getMarketType(), quote.getMarketPeriod(), quote.getLine(), quote.getOutcome()), quote));

        Map<QuoteIdentity, MatchOddsQuote> pending = new LinkedHashMap<>();
        java.util.Set<QuoteIdentity> changed = new java.util.HashSet<>();
        Instant now = Instant.now();
        for (QuoteBatchInput batch : batches) {
            Match match = batch.match();
            for (Map.Entry<Bookmaker, List<QuoteInput>> entry : batch.valuesByBookmaker().entrySet()) {
                Bookmaker bookmaker = entry.getKey();
                if (bookmaker == null || bookmaker.getId() == null || entry.getValue() == null) continue;
                for (QuoteInput value : entry.getValue()) {
                    if (value == null || !StringUtils.hasText(value.market())
                            || !StringUtils.hasText(value.outcome()) || value.odd() == null
                            || value.odd().signum() <= 0) continue;
                    MatchOddsMarketType type = marketType(value.market());
                    MatchOddsPeriod period = marketPeriod(value.market());
                    String outcome = normalizeOutcome(value.outcome());
                    BigDecimal line = normalizeLine(value.line(), value.outcome());
                    QuoteIdentity key = identity(match.getId(), bookmaker.getId(), type, period, line, outcome);
                    MatchOddsQuote quote = pending.get(key);
                    if (quote == null) quote = existing.get(key);
                    if (quote == null) {
                        quote = MatchOddsQuote.builder().match(match).bookmaker(bookmaker)
                                .provider(provider).marketType(type).marketPeriod(period)
                                .line(line).outcome(outcome).build();
                    }
                    BigDecimal previous = quote.getOdd();
                    if (previous == null || previous.compareTo(value.odd()) != 0) changed.add(key);
                    quote.setOdd(value.odd());
                    quote.setFetchedAt(now);
                    pending.put(key, quote);
                }
            }
        }
        if (pending.isEmpty()) return;
        List<MatchOddsQuote> saved = quoteRepository.saveAll(pending.values());
        Map<QuoteIdentity, MatchOddsQuote> savedByKey = new LinkedHashMap<>();
        saved.forEach(quote -> savedByKey.put(identity(quote.getMatch().getId(),
                quote.getBookmaker().getId(), quote.getMarketType(), quote.getMarketPeriod(),
                quote.getLine(), quote.getOutcome()), quote));

        List<OddsHistory> histories = new ArrayList<>();
        for (QuoteIdentity key : changed) {
            MatchOddsQuote quote = savedByKey.get(key);
            if (quote != null) {
                histories.add(OddsHistory.builder().quote(quote).outcome(quote.getOutcome())
                        .line(quote.getLine()).odd(quote.getOdd()).recordedAt(now).build());
            }
        }
        if (!histories.isEmpty()) historyRepository.saveAll(histories);
    }

    public record QuoteBatchInput(Match match, MatchProvider provider,
                                  Map<Bookmaker, List<QuoteInput>> valuesByBookmaker) {}

    private static QuoteIdentity identity(Long matchId, Long bookmakerId, MatchOddsMarketType type,
                                          MatchOddsPeriod period, BigDecimal line, String outcome) {
        return new QuoteIdentity(matchId, bookmakerId, type, period,
                line == null ? BigDecimal.ZERO.setScale(3) : line.setScale(3, java.math.RoundingMode.HALF_UP),
                outcome);
    }

    public record QuoteInput(String market, String outcome, BigDecimal line, BigDecimal odd) {
        public QuoteInput(String market, String outcome, BigDecimal odd) {
            this(market, outcome, null, odd);
        }
    }

    private record QuoteIdentity(Long matchId, Long bookmakerId, MatchOddsMarketType type,
                                 MatchOddsPeriod period, BigDecimal line, String outcome) {}

    private static MatchOddsMarketType marketType(String market) {
        String value = market.toLowerCase(Locale.ROOT);
        if (value.contains("match winner") || value.contains("1x2") || value.contains("full time result")) {
            return MatchOddsMarketType.MATCH_RESULT;
        }
        if (value.contains("over") || value.contains("under") || value.contains("goals") || value.contains("ou25")) {
            return MatchOddsMarketType.OVER_UNDER;
        }
        if (value.contains("both teams") || value.contains("btts")) {
            return MatchOddsMarketType.BOTH_TEAMS_TO_SCORE;
        }
        if (value.contains("handicap") || value.contains("ah_") || value.equals("ah")) {
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
        return MatchOddsPeriod.PRE_MATCH_CURRENT;
    }

    private static String normalizeOutcome(String outcome) {
        String value = outcome.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
        return switch (value) {
            case "HOME", "1" -> "HOME";
            case "DRAW", "X" -> "DRAW";
            case "AWAY", "2" -> "AWAY";
            case "YES" -> "YES";
            case "NO" -> "NO";
            default -> value.startsWith("OVER_") ? "OVER"
                    : value.startsWith("UNDER_") ? "UNDER"
                    : value.length() > 40 ? value.substring(0, 40) : value;
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
