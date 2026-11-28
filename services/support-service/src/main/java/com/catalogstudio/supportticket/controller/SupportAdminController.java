package com.catalogstudio.supportticket.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.supportticket.dto.AdminStatsResponse;
import com.catalogstudio.supportticket.dto.AssignTicketRequest;
import com.catalogstudio.supportticket.dto.CategoryRequest;
import com.catalogstudio.supportticket.dto.CategoryResponse;
import com.catalogstudio.supportticket.dto.CloseTicketRequest;
import com.catalogstudio.supportticket.dto.KnowledgeArticleRequest;
import com.catalogstudio.supportticket.dto.KnowledgeArticleResponse;
import com.catalogstudio.supportticket.dto.SlaConfigRequest;
import com.catalogstudio.supportticket.dto.SlaConfigResponse;
import com.catalogstudio.supportticket.dto.TicketReplyRequest;
import com.catalogstudio.supportticket.dto.TicketResponse;
import com.catalogstudio.supportticket.dto.TicketSummaryResponse;
import com.catalogstudio.supportticket.dto.UpdatePriorityRequest;
import com.catalogstudio.supportticket.dto.UpdateStatusRequest;
import com.catalogstudio.supportticket.knowledge.KnowledgeAdminService;
import com.catalogstudio.supportticket.service.AdminTicketService;
import com.catalogstudio.supportticket.service.CategoryAdminService;
import com.catalogstudio.supportticket.sla.SlaAdminService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support/admin")
@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
@RequiredArgsConstructor
public class SupportAdminController {

    private final AdminTicketService adminTicketService;
    private final CategoryAdminService categoryAdminService;
    private final KnowledgeAdminService knowledgeAdminService;
    private final SlaAdminService slaAdminService;

    @GetMapping("/stats")
    public ApiResponse<AdminStatsResponse> stats() {
        return ApiResponse.ok(adminTicketService.stats());
    }

    @GetMapping("/tickets")
    public ApiResponse<Page<TicketSummaryResponse>> tickets(
            @RequestParam(required = false, defaultValue = "OPEN") String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long assignee,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Boolean slaBreached,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(adminTicketService.search(
                status, priority, category, assignee, from, to, slaBreached, q, pageable));
    }

    @GetMapping("/tickets/{id}")
    public ApiResponse<TicketResponse> ticket(@PathVariable String id) {
        return ApiResponse.ok(adminTicketService.get(id));
    }

    @PostMapping("/tickets/{id}/assign")
    public ApiResponse<TicketResponse> assign(
            @PathVariable String id,
            @Valid @RequestBody AssignTicketRequest request) {
        return ApiResponse.ok(adminTicketService.assign(id, request));
    }

    @PatchMapping("/tickets/{id}/priority")
    public ApiResponse<TicketResponse> priority(
            @PathVariable String id,
            @Valid @RequestBody UpdatePriorityRequest request) {
        return ApiResponse.ok(adminTicketService.updatePriority(id, request));
    }

    @PatchMapping("/tickets/{id}/status")
    public ApiResponse<TicketResponse> status(
            @PathVariable String id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ApiResponse.ok(adminTicketService.updateStatus(id, request));
    }

    @PostMapping("/tickets/{id}/replies")
    public ApiResponse<TicketResponse> reply(
            @PathVariable String id,
            @Valid @RequestBody TicketReplyRequest request) {
        return ApiResponse.ok(adminTicketService.reply(id, request));
    }

    @PostMapping("/tickets/{id}/close")
    public ApiResponse<TicketResponse> close(
            @PathVariable String id,
            @RequestBody(required = false) CloseTicketRequest request) {
        return ApiResponse.ok(adminTicketService.close(id, request == null ? new CloseTicketRequest(null) : request));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> categories() {
        return ApiResponse.ok(categoryAdminService.list());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryAdminService.create(request));
    }

    @PutMapping("/categories/{uuid}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable UUID uuid,
            @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryAdminService.update(uuid, request));
    }

    @DeleteMapping("/categories/{uuid}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<Void> deleteCategory(@PathVariable UUID uuid) {
        categoryAdminService.delete(uuid);
        return ApiResponse.okMessage("Category deactivated");
    }

    @GetMapping("/knowledge")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<Page<KnowledgeArticleResponse>> knowledge(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(knowledgeAdminService.list(pageable));
    }

    @GetMapping("/knowledge/{uuid}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<KnowledgeArticleResponse> knowledgeOne(@PathVariable UUID uuid) {
        return ApiResponse.ok(knowledgeAdminService.get(uuid));
    }

    @PostMapping("/knowledge")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<KnowledgeArticleResponse> createKnowledge(@Valid @RequestBody KnowledgeArticleRequest request) {
        return ApiResponse.ok(knowledgeAdminService.create(request));
    }

    @PutMapping("/knowledge/{uuid}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<KnowledgeArticleResponse> updateKnowledge(
            @PathVariable UUID uuid,
            @Valid @RequestBody KnowledgeArticleRequest request) {
        return ApiResponse.ok(knowledgeAdminService.update(uuid, request));
    }

    @DeleteMapping("/knowledge/{uuid}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<Void> deleteKnowledge(@PathVariable UUID uuid) {
        knowledgeAdminService.delete(uuid);
        return ApiResponse.okMessage("Article deactivated");
    }

    @GetMapping("/sla")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<SlaConfigResponse> getSla() {
        return ApiResponse.ok(slaAdminService.getActive());
    }

    @PutMapping("/sla")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<SlaConfigResponse> putSla(@Valid @RequestBody SlaConfigRequest request) {
        return ApiResponse.ok(slaAdminService.updateActive(request));
    }
}
