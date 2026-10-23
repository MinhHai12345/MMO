package com.mmo.module.fb.content.production.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.AppProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Official Telegram Bot API adapter. The configured bot must be an administrator
 * in the target channel (or otherwise have permission to post there).
 */
@Component
public class TelegramBotApiPublisher {
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_PHOTO_CAPTION_LENGTH = 1024;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public TelegramBotApiPublisher(RestTemplate restTemplate, ObjectMapper objectMapper,
                                   AppProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public TelegramPublishResult publish(String title, String body, String mediaReference) {
        String token = properties.getTelegram().getBot().getToken();
        String chatId = properties.getTelegram().getChannel().getId();
        if (isUnconfigured(token) || isUnconfigured(chatId)) {
            throw new IllegalStateException("Telegram bot token and channel ID must be configured");
        }

        String text = (title == null || title.isBlank()) ? body : title.strip() + "\n\n" + body.strip();
        if (mediaReference != null && !mediaReference.isBlank()) {
            if (text.codePointCount(0, text.length()) > MAX_PHOTO_CAPTION_LENGTH) {
                throw new IllegalArgumentException("Telegram photo caption must not exceed 1024 characters");
            }
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("chat_id", chatId);
            request.put("photo", mediaReference.strip());
            request.put("caption", text);
            JsonNode result = call(token, "sendPhoto", request);
            return result(result, Instant.now());
        }

        if (text.codePointCount(0, text.length()) > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Telegram message including title must not exceed 4096 characters");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("chat_id", chatId);
        request.put("text", text);
        request.put("disable_web_page_preview", true);
        return result(call(token, "sendMessage", request), Instant.now());
    }

    private JsonNode call(String token, String method, Map<String, Object> request) {
        String url = "https://api.telegram.org/bot" + token + "/" + method;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            String body = restTemplate.postForObject(url, new HttpEntity<>(request, headers), String.class);
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            if (!root.path("ok").asBoolean(false) || !root.has("result")) {
                String description = root.path("description").asText("Telegram Bot API returned an unsuccessful response");
                throw new TelegramPublishingException(description);
            }
            return root.path("result");
        } catch (RestClientException ex) {
            throw new TelegramPublishingException("Telegram Bot API request failed", ex);
        } catch (java.io.IOException ex) {
            throw new TelegramPublishingException("Unable to parse Telegram Bot API response", ex);
        }
    }

    private static TelegramPublishResult result(JsonNode result, Instant completedAt) {
        JsonNode messageId = result.path("message_id");
        if (messageId.isMissingNode() || messageId.isNull()) {
            throw new TelegramPublishingException("Telegram response did not contain message_id");
        }
        String chatId = result.path("chat").path("id").asText("");
        return new TelegramPublishResult(messageId.asText(), chatId, completedAt);
    }

    private static boolean isUnconfigured(String value) {
        return value == null || value.isBlank() || "mock".equalsIgnoreCase(value.strip());
    }

    public record TelegramPublishResult(String messageId, String chatId, Instant completedAt) {}

    public static class TelegramPublishingException extends RuntimeException {
        public TelegramPublishingException(String message) { super(message); }
        public TelegramPublishingException(String message, Throwable cause) { super(message, cause); }
    }
}
