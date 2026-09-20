package com.catalogstudio.auth.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OtpResendRequest(@NotNull UUID challengeId) {}
