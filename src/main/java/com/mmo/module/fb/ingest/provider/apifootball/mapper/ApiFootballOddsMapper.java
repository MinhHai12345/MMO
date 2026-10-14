package com.mmo.module.fb.ingest.provider.apifootball.mapper;

import com.mmo.core.converter.AbstractMapper;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.OddsBetDto;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.OddsBookmakerDto;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.OddsItem;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.OddsValueDto;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class ApiFootballOddsMapper extends AbstractMapper<ApiFootballOddsMapper.OddsMappingInput, ProviderOdds> {

    @Override
    public ProviderOdds map(OddsMappingInput input, ProviderOdds target) {
        if (input == null || input.bookmaker() == null || input.bet() == null || input.value() == null
                || input.bookmaker().name() == null || input.bet().name() == null
                || input.value().value() == null) {
            return null;
        }
        BigDecimal odd = decimal(input.value().odd());
        if (odd == null) return null;
        return new ProviderOdds(
                input.bookmaker().name(),
                input.bet().name(),
                input.value().value(),
                odd
        );
    }

    public List<ProviderOdds> toModels(List<OddsItem> items) {
        List<ProviderOdds> result = new ArrayList<>();
        if (items == null) return result;
        for (OddsItem item : items) {
            if (item == null || item.bookmakers() == null) continue;
            for (OddsBookmakerDto bookmaker : item.bookmakers()) {
                if (bookmaker == null || bookmaker.bets() == null) continue;
                for (OddsBetDto bet : bookmaker.bets()) {
                    if (bet == null || bet.values() == null) continue;
                    for (OddsValueDto value : bet.values()) {
                        ProviderOdds mapped = map(new OddsMappingInput(bookmaker, bet, value), null);
                        if (mapped != null) result.add(mapped);
                    }
                }
            }
        }
        return result;
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record OddsMappingInput(
            OddsBookmakerDto bookmaker,
            OddsBetDto bet,
            OddsValueDto value
    ) {
    }
}
