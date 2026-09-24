package com.catalogstudio.admin.controller;

import com.catalogstudio.access.FeatureCatalog;
import com.catalogstudio.admin.dto.AdminSubscriptionActionRequest;
import com.catalogstudio.admin.dto.AdminUserRow;
import com.catalogstudio.admin.dto.FeatureAccessUpdateRequest;
import com.catalogstudio.admin.dto.StaffRoleActionRequest;
import com.catalogstudio.admin.service.AdminWorkspaceService;
import com.catalogstudio.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/workspace")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin workspace")
public class AdminWorkspaceController {

    private final AdminWorkspaceService workspaceService;

    @GetMapping("/users")
    public ApiResponse<Page<AdminUserRow>> users(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return ApiResponse.ok(workspaceService.listWorkspaceUsers(q, page, size, sort));
    }

    @PutMapping("/users/{id}/subscription")
    public ApiResponse<AdminUserRow> subscription(
            @PathVariable UUID id,
            @Valid @RequestBody AdminSubscriptionActionRequest request
    ) {
        return ApiResponse.ok("Subscription updated", workspaceService.applySubscription(id, request));
    }

    @GetMapping("/access")
    public ApiResponse<Page<AdminUserRow>> access(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return ApiResponse.ok(workspaceService.listWorkspaceUsers(q, page, size, sort));
    }

    @PutMapping("/access/{id}")
    public ApiResponse<AdminUserRow> updateAccess(
            @PathVariable UUID id,
            @Valid @RequestBody FeatureAccessUpdateRequest request
    ) {
        return ApiResponse.ok("Access updated", workspaceService.updateAccess(id, request.features()));
    }

    @GetMapping("/features")
    public ApiResponse<List<FeatureCatalog.FeatureDefinition>> features() {
        return ApiResponse.ok(workspaceService.features());
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<Page<AdminUserRow>> staff(
            @RequestParam(defaultValue = "ADMIN") String tab,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return ApiResponse.ok(workspaceService.listStaff(tab, q, page, size, sort));
    }

    @PutMapping("/staff/{id}/role")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<AdminUserRow> staffRole(
            @PathVariable UUID id,
            @Valid @RequestBody StaffRoleActionRequest request
    ) {
        return ApiResponse.ok("Role updated", workspaceService.applyStaffRole(id, request));
    }
}
