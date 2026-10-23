package com.mmo.module.fb.content.service.impl;

import com.mmo.module.fb.content.api.ApprovedContentReader;
import com.mmo.module.fb.content.entity.ContentDraftRecordEntity;
import com.mmo.module.fb.content.repository.ContentDraftRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovedContentReaderImpl implements ApprovedContentReader {
    private final ContentDraftRecordRepository draftRepository;

    @Override
    public ApprovedContent requireApproved(Long draftId) {
        if (draftId == null || draftId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive draftId is required");
        }
        ContentDraftRecordEntity draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content draft not found"));
        if (!"APPROVED".equalsIgnoreCase(draft.getEditorialStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The source content draft must be editorially APPROVED first");
        }
        if ("BLOCKED".equalsIgnoreCase(draft.getGenerationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Blocked content drafts cannot be published");
        }
        return new ApprovedContent(draft.getId(), draft.getVersionNumber(), draft.getTitle());
    }
}
