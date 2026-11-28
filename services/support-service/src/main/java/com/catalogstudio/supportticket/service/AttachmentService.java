package com.catalogstudio.supportticket.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.security.AuthUser;
import com.catalogstudio.security.Roles;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.support.storage.SupportStorageService;
import com.catalogstudio.supportticket.dto.AttachmentResponse;
import com.catalogstudio.supportticket.entity.SupportTicket;
import com.catalogstudio.supportticket.entity.SupportTicketAttachment;
import com.catalogstudio.supportticket.repository.SupportTicketAttachmentRepository;
import com.catalogstudio.supportticket.repository.SupportTicketRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final SupportTicketAttachmentRepository attachmentRepository;
    private final SupportTicketRepository ticketRepository;
    private final SupportStorageService storageService;

    @Transactional
    public AttachmentResponse uploadForTicket(String ticketKey, MultipartFile file) {
        AuthUser user = SecurityUtils.currentUser();
        SupportTicket ticket = resolveTicket(ticketKey);
        assertCanAccess(ticket, user);
        SupportStorageService.StoredFile stored = storageService.store(file);
        SupportTicketAttachment attachment = attachmentRepository.save(SupportTicketAttachment.builder()
                .ticketId(ticket.getId())
                .storedName(stored.storedName())
                .originalName(stored.originalName())
                .contentType(stored.contentType())
                .sizeBytes(stored.sizeBytes())
                .checksum(stored.checksum())
                .createdByUserId(user.id())
                .build());
        return SupportMapper.toAttachment(attachment);
    }

    @Transactional
    public List<SupportTicketAttachment> claimPending(List<UUID> attachmentIds, Long userId, Long ticketId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        List<SupportTicketAttachment> found =
                attachmentRepository.findByUuidInAndCreatedByUserId(attachmentIds, userId);
        if (found.size() != attachmentIds.stream().distinct().count()) {
            throw ApiException.badRequest("One or more attachments were not found");
        }
        for (SupportTicketAttachment attachment : found) {
            if (attachment.getTicketId() != null && !attachment.getTicketId().equals(ticketId)) {
                throw ApiException.badRequest("Attachment already linked to another ticket");
            }
            attachment.setTicketId(ticketId);
        }
        return attachmentRepository.saveAll(found);
    }

    @Transactional
    public SupportTicketAttachment storePending(MultipartFile file) {
        AuthUser user = SecurityUtils.currentUser();
        SupportStorageService.StoredFile stored = storageService.store(file);
        return attachmentRepository.save(SupportTicketAttachment.builder()
                .storedName(stored.storedName())
                .originalName(stored.originalName())
                .contentType(stored.contentType())
                .sizeBytes(stored.sizeBytes())
                .checksum(stored.checksum())
                .createdByUserId(user.id())
                .build());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> download(UUID uuid) {
        AuthUser user = SecurityUtils.currentUser();
        SupportTicketAttachment attachment = attachmentRepository.findByUuid(uuid)
                .orElseThrow(() -> ApiException.notFound("Attachment not found"));
        if (attachment.getTicketId() != null) {
            SupportTicket ticket = ticketRepository.findById(attachment.getTicketId())
                    .orElseThrow(() -> ApiException.notFound("Attachment not found"));
            assertCanAccess(ticket, user);
        } else if (!attachment.getCreatedByUserId().equals(user.id()) && !Roles.isStaff(user.role())) {
            throw ApiException.forbidden("You cannot download this attachment");
        }
        byte[] bytes = storageService.read(attachment.getStoredName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getOriginalName().replace("\"", "") + "\"")
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .body(bytes);
    }

    private SupportTicket resolveTicket(String key) {
        if (key == null || key.isBlank()) {
            throw ApiException.badRequest("Ticket id is required");
        }
        try {
            UUID uuid = UUID.fromString(key);
            return ticketRepository.findByUuidAndDeletedAtIsNull(uuid)
                    .orElseThrow(() -> ApiException.notFound("Ticket not found"));
        } catch (IllegalArgumentException ignored) {
            return ticketRepository.findByTicketNumberAndDeletedAtIsNull(key.trim().toUpperCase())
                    .or(() -> ticketRepository.findByTicketNumberAndDeletedAtIsNull(key.trim()))
                    .orElseThrow(() -> ApiException.notFound("Ticket not found"));
        }
    }

    private void assertCanAccess(SupportTicket ticket, AuthUser user) {
        if (Roles.isStaff(user.role())) {
            return;
        }
        if (!ticket.getCustomerUserId().equals(user.id())) {
            throw ApiException.forbidden("You cannot access this ticket");
        }
    }
}
