package com.mmo.module.fb.content.publish;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationPayloadValidatorTest {

    @Test
    void acceptsXPostWithinCharacterLimit() {
        assertDoesNotThrow(() -> PublicationPayloadValidator.validate(
                PublicationPlatform.X, "Preview", "A".repeat(280), null));
    }

    @Test
    void rejectsXPostAboveCharacterLimit() {
        assertThrows(ResponseStatusException.class, () -> PublicationPayloadValidator.validate(
                PublicationPlatform.X, "Preview", "A".repeat(281), null));
    }

    @Test
    void requiresMediaForVideoAndImageDestinations() {
        assertThrows(ResponseStatusException.class, () -> PublicationPayloadValidator.validate(
                PublicationPlatform.YOUTUBE, "Match analysis", "Description", null));
        assertThrows(ResponseStatusException.class, () -> PublicationPayloadValidator.validate(
                PublicationPlatform.INSTAGRAM, "Match analysis", "Caption", null));
        assertThrows(ResponseStatusException.class, () -> PublicationPayloadValidator.validate(
                PublicationPlatform.TIKTOK, "Match analysis", "Caption", null));
    }

    @Test
    void countsSurrogatePairsConservativelyForX() {
        assertDoesNotThrow(() -> PublicationPayloadValidator.validate(
                PublicationPlatform.X, "Preview", "😀".repeat(140), null));
        assertThrows(ResponseStatusException.class, () -> PublicationPayloadValidator.validate(
                PublicationPlatform.X, "Preview", "😀".repeat(141), null));
    }
}
