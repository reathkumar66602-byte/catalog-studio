package com.catalogstudio.auth.controller;

import com.catalogstudio.auth.dto.AuthFlowResponse;
import com.catalogstudio.auth.dto.ForgotPasswordRequest;
import com.catalogstudio.auth.dto.LoginRequest;
import com.catalogstudio.auth.dto.OtpResendRequest;
import com.catalogstudio.auth.dto.OtpVerifyRequest;
import com.catalogstudio.auth.dto.RefreshTokenRequest;
import com.catalogstudio.auth.dto.RegisterRequest;
import com.catalogstudio.auth.dto.ResetPasswordRequest;
import com.catalogstudio.auth.dto.VerifyEmailRequest;
import com.catalogstudio.auth.service.AuthService;
import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.security.AuthUser;
import com.catalogstudio.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a seller account")
    public ApiResponse<AuthFlowResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return ApiResponse.ok("Account created. Verify the OTP sent to your email.",
                authService.register(request, clientIp(http), http.getHeader("User-Agent")));
    }

    @PostMapping("/login")
    @Operation(summary = "Seller login with email/username and password")
    public ApiResponse<AuthFlowResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ApiResponse.ok("Signed in successfully",
                authService.login(request, clientIp(http), http.getHeader("User-Agent")));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify email OTP")
    public ApiResponse<AuthFlowResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request, HttpServletRequest http) {
        return ApiResponse.ok("Verified successfully",
                authService.verifyOtp(request, clientIp(http), http.getHeader("User-Agent")));
    }

    @PostMapping("/otp/resend")
    @Operation(summary = "Resend email OTP")
    public ApiResponse<AuthFlowResponse.OtpChallengeView> resendOtp(@Valid @RequestBody OtpResendRequest request) {
        return ApiResponse.ok("A new OTP has been sent", authService.resendOtp(request));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh access token")
    public ApiResponse<com.catalogstudio.auth.dto.AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout current session")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.okMessage("Logged out");
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email with token")
    public ApiResponse<Void> verify(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ApiResponse.okMessage("Email verified");
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset")
    public ApiResponse<Void> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.okMessage("If the email exists, a reset link has been sent");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with token")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.okMessage("Password updated");
    }

    @PostMapping("/logout-others")
    @Operation(summary = "Revoke other devices")
    public ApiResponse<Void> logoutOthers(@RequestBody(required = false) RefreshTokenRequest request) {
        AuthUser user = SecurityUtils.currentUser();
        authService.logoutOtherDevices(user.id(), request == null ? null : request.refreshToken());
        return ApiResponse.okMessage("Other devices signed out");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
