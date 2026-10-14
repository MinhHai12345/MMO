package com.mmo.module.fb.ingest.provider.apifootball.mapper;

import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.OddsItem;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.OddsBookmakerDto;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.OddsBetDto;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.OddsValueDto;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class ApiFootballOddsMapper {

    public List<ProviderOdds> toModels(List<OddsItem> items) {
        List<ProviderOdds> result = new ArrayList<>();
        if (items == null) return result;
        for (OddsItem item : items) {
            if (item == null || item.bookmakers() == null) continue;
            for (OddsBookmakerDto bookmaker : item.bookmakers()) {
                if (bookmaker == null || bookmaker.name() == null || bookmaker.bets() == null) continue;
                for (OddsBetDto bet : bookmaker.bets()) {
                    if (bet == null || bet.name() == null || bet.values() == null) continue;
                    for (OddsValueDto value : bet.values()) {
                        if (value == null || value.value() == null) continue;
                        BigDecimal odd = decimal(value.odd());
                        if (odd != null) {
                            // market is the provider's bet type; outcome is the value within that market.
                            result.add(new ProviderOdds(bookmaker.name(), bet.name(), value.value(), odd));
                        }
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
}
