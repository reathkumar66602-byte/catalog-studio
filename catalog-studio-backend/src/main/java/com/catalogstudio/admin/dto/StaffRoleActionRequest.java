package com.catalogstudio.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record StaffRoleActionRequest(
        @NotBlank String action
) {}
