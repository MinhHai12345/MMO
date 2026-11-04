package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.model.ProviderMatchAdvancedStatistics;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.GroupDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.PeriodStatisticsDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.StatisticItemDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Converts SofaScore transport statistics into canonical provider-neutral keys and values. */
@Component
public class SofaScoreMatchStatisticsMapper {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:[.,]\\d+)?");

    public Optional<ProviderMatchAdvancedStatistics> toProviderStatistics(
            String externalMatchId, List<PeriodStatisticsDTO> periods) {
        if (periods == null || periods.isEmpty()) return Optional.empty();

        List<StatisticItemDTO> items = periods.stream()
                .filter(java.util.Objects::nonNull)
                .filter(period -> "ALL".equalsIgnoreCase(period.period()))
                .findFirst()
                .map(PeriodStatisticsDTO::groups)
                .orElseGet(() -> periods.stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(period -> period.groups() != null && !period.groups().isEmpty())
                        .findFirst()
                        .map(PeriodStatisticsDTO::groups)
                        .orElse(Collections.emptyList()))
                .stream()
                .filter(java.util.Objects::nonNull)
                .map(GroupDTO::statisticsItems)
                .filter(list -> list != null && !list.isEmpty())
                .flatMap(List::stream)
                .filter(java.util.Objects::nonNull)
                .toList();

        if (items.isEmpty()) return Optional.empty();
        Map<String, BigDecimal> home = new HashMap<>();
        Map<String, BigDecimal> away = new HashMap<>();
        for (StatisticItemDTO item : items) {
            BigDecimal homeValue = parse(item.homeValue(), item.home());
            BigDecimal awayValue = parse(item.awayValue(), item.away());
            put(home, item.key(), homeValue);
            put(home, item.name(), homeValue);
            put(away, item.key(), awayValue);
            put(away, item.name(), awayValue);
        }
        return Optional.of(new ProviderMatchAdvancedStatistics(externalMatchId, home, away));
    }

    private void put(Map<String, BigDecimal> target, String key, BigDecimal value) {
        String normalized = normalize(key);
        if (!normalized.isBlank() && value != null && value.signum() >= 0) {
            target.putIfAbsent(normalized, value);
        }
    }

    private BigDecimal parse(BigDecimal typedValue, String rawValue) {
        if (typedValue != null && typedValue.signum() >= 0) return typedValue;
        if (!StringUtils.hasText(rawValue) || "-".equals(rawValue.trim())) return null;
        Matcher matcher = NUMBER.matcher(rawValue);
        if (!matcher.find()) return null;
        try {
            return new BigDecimal(matcher.group().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
