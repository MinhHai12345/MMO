package com.mmo.module.fb.ingest.provider.footballdatauk;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public final class FootballDataUkCsvParser {
    public List<Map<String, String>> parse(String csv) {
        if (!StringUtils.hasText(csv)) {
            throw invalid("CSV content is empty");
        }

        List<List<String>> rows = readRows(csv);
        if (rows.isEmpty()) throw invalid("CSV does not contain a header row");

        List<String> headers = rows.get(0).stream()
                .map(value -> value.trim().replace("\uFEFF", "").toUpperCase(Locale.ROOT)).toList();
        boolean hasDate = headers.contains("DATE");
        boolean hasHome = headers.contains("HOMETEAM") || headers.contains("HOME TEAM");
        boolean hasAway = headers.contains("AWAYTEAM") || headers.contains("AWAY TEAM");
        if (!hasDate || !hasHome || !hasAway || headers.size() < 3) {
            throw invalid("CSV header does not contain DATE, HomeTeam and AwayTeam");
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> values = rows.get(i);
            if (values.stream().allMatch(String::isBlank)) continue;
            if (values.size() > headers.size()) {
                throw invalid("CSV row " + (i + 1) + " has more columns than the header");
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                row.put(headers.get(column), column < values.size() ? values.get(column).trim() : "");
            }
            result.add(row);
        }
        return result;
    }

    private List<List<String>> readRows(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                row.add(value.toString());
                value.setLength(0);
            } else if ((c == '\n' || c == '\r') && !quoted) {
                if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
                row.add(value.toString());
                value.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                value.append(c);
            }
        }
        if (quoted) throw invalid("CSV contains an unterminated quoted field");
        if (!value.isEmpty() || !row.isEmpty()) {
            row.add(value.toString());
            rows.add(row);
        }
        return rows;
    }

    private FootballDataUkCsvException invalid(String message) {
        return new FootballDataUkCsvException(
                FootballDataUkCsvException.Reason.INVALID_CSV, message);
    }
}
