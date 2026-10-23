package com.mmo.module.fb.content.production.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TelegramBotApiPublisherTest {
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private TelegramBotApiPublisher publisher;
    private AppProperties properties;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        properties = new AppProperties();
        properties.getTelegram().getBot().setToken("test-token");
        properties.getTelegram().getChannel().setId("@football_test");
        publisher = new TelegramBotApiPublisher(restTemplate, new ObjectMapper(), properties);
    }

    @Test
    void sendsTextToOfficialSendMessageEndpointAndReturnsMessageId() {
        server.expect(requestTo("https://api.telegram.org/bottest-token/sendMessage"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.chat_id").value("@football_test"))
                .andExpect(jsonPath("$.text").value("Match preview\n\nHome vs Away"))
                .andRespond(withSuccess("""
                        {"ok":true,"result":{"message_id":123,"chat":{"id":-100123}}}
                        """, MediaType.APPLICATION_JSON));

        var result = publisher.publish("Match preview", "Home vs Away", null);
        assertEquals("123", result.messageId());
        assertEquals("-100123", result.chatId());
        server.verify();
    }

    @Test
    void sendsPhotoWhenMediaReferenceIsProvided() {
        server.expect(requestTo("https://api.telegram.org/bottest-token/sendPhoto"))
                .andExpect(jsonPath("$.photo").value("AgAC-file-id"))
                .andExpect(jsonPath("$.caption").value("Preview\n\nHome vs Away"))
                .andRespond(withSuccess("""
                        {"ok":true,"result":{"message_id":124,"chat":{"id":-100123}}}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("124", publisher.publish("Preview", "Home vs Away", "AgAC-file-id").messageId());
        server.verify();
    }

    @Test
    void rejectsUnconfiguredCredentialsBeforeNetworkCall() {
        properties.getTelegram().getBot().setToken("mock");
        assertThrows(IllegalStateException.class, () -> publisher.publish("Title", "Body", null));
        server.verify();
    }

    @Test
    void rejectsTelegramApiFailure() {
        server.expect(requestTo("https://api.telegram.org/bottest-token/sendMessage"))
                .andRespond(withSuccess("""
                        {"ok":false,"description":"chat not found"}
                        """, MediaType.APPLICATION_JSON));
        var failure = assertThrows(TelegramBotApiPublisher.TelegramPublishingException.class,
                () -> publisher.publish("Title", "Body", null));
        assertTrue(failure.getMessage().contains("chat not found"));
        server.verify();
    }

    @Test
    void rejectsPhotoCaptionOverTelegramLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> publisher.publish("title", "x".repeat(1024), "AgAC-file-id"));
        server.verify();
    }
}
