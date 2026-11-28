package com.catalogstudio.supportticket.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.supportticket.dto.AttachmentResponse;
import com.catalogstudio.supportticket.dto.CategoryResponse;
import com.catalogstudio.supportticket.dto.CreateTicketRequest;
import com.catalogstudio.supportticket.dto.TicketReplyRequest;
import com.catalogstudio.supportticket.dto.TicketResponse;
import com.catalogstudio.supportticket.dto.TicketSummaryResponse;
import com.catalogstudio.supportticket.service.AttachmentService;
import com.catalogstudio.supportticket.service.CategoryAdminService;
import com.catalogstudio.supportticket.service.SupportMapper;
import com.catalogstudio.supportticket.service.TicketService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportTicketController {

    private final TicketService ticketService;
    private final AttachmentService attachmentService;
    private final CategoryAdminService categoryAdminService;

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> activeCategories() {
        return ApiResponse.ok(categoryAdminService.listActive());
    }

    @PostMapping("/tickets")
    public ApiResponse<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        return ApiResponse.ok(ticketService.create(request));
    }

    @GetMapping("/tickets")
    public ApiResponse<Page<TicketSummaryResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        String query = search != null ? search : q;
        return ApiResponse.ok(ticketService.listMine(status, query, pageable));
    }

    @GetMapping("/tickets/{id}")
    public ApiResponse<TicketResponse> get(@PathVariable String id) {
        return ApiResponse.ok(ticketService.getMine(id));
    }

    @PostMapping("/tickets/{id}/replies")
    public ApiResponse<TicketResponse> reply(
            @PathVariable String id,
            @Valid @RequestBody TicketReplyRequest request) {
        return ApiResponse.ok(ticketService.reply(id, request));
    }

    @PostMapping(value = "/tickets/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AttachmentResponse> uploadAttachment(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(attachmentService.uploadForTicket(id, file));
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AttachmentResponse> uploadPending(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(SupportMapper.toAttachment(attachmentService.storePending(file)));
    }

    @GetMapping("/attachments/{uuid}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID uuid) {
        return attachmentService.download(uuid);
    }

    @PostMapping("/tickets/{id}/reopen")
    public ApiResponse<TicketResponse> reopen(@PathVariable String id) {
        return ApiResponse.ok(ticketService.reopen(id));
    }
}
