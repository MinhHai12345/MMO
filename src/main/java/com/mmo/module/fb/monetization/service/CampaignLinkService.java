package com.mmo.module.fb.monetization.service;

import com.mmo.module.fb.monetization.entity.CampaignLinkEntity;
import com.mmo.module.fb.monetization.repository.CampaignLinkRepository;
import com.mmo.module.fb.monetization.service.ConversionEventService.RecordCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampaignLinkService {
    private final CampaignLinkRepository campaignRepository;
    private final ConversionEventService eventService;

    @Transactional
    public CampaignLinkEntity create(CreateCampaignCommand command) {
        if (command == null || blank(command.campaignCode()) || blank(command.destinationUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "campaignCode and destinationUrl are required");
        }
        String code = command.campaignCode().strip();
        if (code.length() > 100 || !code.matches("[A-Za-z0-9_-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "campaignCode must be 1-100 characters using letters, digits, _ or -");
        }
        String destination = command.destinationUrl().strip();
        URI uri;
        try {
            uri = URI.create(destination);
        } catch (IllegalArgumentException invalidUri) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "destinationUrl is invalid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "destinationUrl must be an absolute HTTPS URL without embedded credentials");
        }
        if (destination.length() > 2048) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "destinationUrl must not exceed 2048 characters");
        }
        if (campaignRepository.findByCampaignCode(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "campaignCode already exists");
        }

        CampaignLinkEntity link = new CampaignLinkEntity();
        link.setCampaignCode(code);
        link.setDestinationUrl(destination);
        link.setPublicationId(command.publicationId());
        link.setActive(true);
        try {
            return campaignRepository.saveAndFlush(link);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "campaignCode already exists", duplicate);
        }
    }

    @Transactional(readOnly = true)
    public CampaignLinkEntity findActive(String code) {
        return campaignRepository.findByCampaignCodeAndActiveTrue(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign link not found"));
    }

    public URI recordClickAndGetDestination(String code) {
        CampaignLinkEntity link = findActive(code);
        String clickId = UUID.randomUUID().toString();
        eventService.record(new RecordCommand(clickId, "mmo-campaign-redirect", clickId,
                "CLICK", link.getPublicationId(), null, link.getCampaignCode(), null, null, Instant.now()));
        return URI.create(link.getDestinationUrl());
    }

    @Transactional
    public CampaignLinkEntity setActive(String code, boolean active) {
        CampaignLinkEntity link = campaignRepository.findByCampaignCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign link not found"));
        link.setActive(active);
        return campaignRepository.save(link);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record CreateCampaignCommand(String campaignCode, String destinationUrl, Long publicationId) {}
}
