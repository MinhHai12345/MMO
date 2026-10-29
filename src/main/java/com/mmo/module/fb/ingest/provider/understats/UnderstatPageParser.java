package com.mmo.module.fb.ingest.provider.understats;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a JSON payload assigned through JSON.parse('...') from an Understat HTML page.
 * The embedded payload uses JavaScript string escapes (including \xNN), so it must be decoded
 * before Jackson can parse the resulting JSON.
 */
@Component
public class UnderstatPageParser {

    public String extractJson(String html, String variableName) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("Understat returned an empty HTML document");
        }
        if (variableName == null || !variableName.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("Invalid Understat page variable name");
        }

        Pattern singleQuoted = Pattern.compile(
                "(?s)\\b(?:var|let|const)\\s+" + Pattern.quote(variableName)
                        + "\\s*=\\s*JSON\\.parse\\('((?:\\\\.|[^'\\\\])*)'\\)");
        Matcher matcher = singleQuoted.matcher(html);
        if (matcher.find()) {
            return decodeJavaScriptString(matcher.group(1));
        }

        Pattern doubleQuoted = Pattern.compile(
                "(?s)\\b(?:var|let|const)\\s+" + Pattern.quote(variableName)
                        + "\\s*=\\s*JSON\\.parse\\(\"((?:\\\\.|[^\"\\\\])*)\"\\)");
        matcher = doubleQuoted.matcher(html);
        if (matcher.find()) {
            return decodeJavaScriptString(matcher.group(1));
        }

        throw new IllegalStateException(
                "Could not find Understat JSON payload '" + variableName
                        + "'. The page layout may have changed or access may be blocked.");
    }

    static String decodeJavaScriptString(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i + 1 >= value.length()) {
                decoded.append(current);
                continue;
            }

            char escaped = value.charAt(++i);
            switch (escaped) {
                case 'n' -> decoded.append('\n');
                case 'r' -> decoded.append('\r');
                case 't' -> decoded.append('\t');
                case 'b' -> decoded.append('\b');
                case 'f' -> decoded.append('\f');
                case 'v' -> decoded.append((char) 0x0B);
                case '\\' -> decoded.append('\\');
                case '\'' -> decoded.append('\'');
                case '"' -> decoded.append('"');
                case '\n' -> { /* JavaScript line continuation: omit both characters. */ }
                case 'x' -> {
                    if (i + 2 >= value.length()) {
                        throw new IllegalArgumentException("Invalid \\x escape in Understat payload");
                    }
                    decoded.append((char) Integer.parseInt(value.substring(i + 1, i + 3), 16));
                    i += 2;
                }
                case 'u' -> {
                    if (i + 4 >= value.length()) {
                        throw new IllegalArgumentException("Invalid \\u escape in Understat payload");
                    }
                    decoded.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> decoded.append(escaped);
            }
        }
        return decoded.toString();
    }
}
