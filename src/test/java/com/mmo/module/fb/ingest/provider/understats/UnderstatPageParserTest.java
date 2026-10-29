package com.mmo.module.fb.ingest.provider.understats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnderstatPageParserTest {

    private final UnderstatPageParser parser = new UnderstatPageParser();

    @Test
    void extractsAndDecodesEmbeddedJson() {
        String html = "var datesData = JSON.parse('[{\\\"id\\\":\\\"31180\\\","
                + "\\\"title\\\":\\\"Chelsea\\\\x20FC\\\"}]');";

        assertEquals("[{\"id\":\"31180\",\"title\":\"Chelsea FC\"}]",
                parser.extractJson(html, "datesData"));
    }

    @Test
    void failsClearlyWhenPageVariableIsMissing() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> parser.extractJson("<html>access denied</html>", "datesData"));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("Could not find"));
    }
}
