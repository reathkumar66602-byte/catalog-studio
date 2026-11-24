package com.catalogstudio.internal.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.internal.InternalApiSupport;
import com.catalogstudio.internal.dto.InternalUserResponse;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final InternalApiSupport internalApiSupport;
    private final UserRepository userRepository;

    @GetMapping("/{id}")
    public ApiResponse<InternalUserResponse> getById(@PathVariable Long id, HttpServletRequest request) {
        internalApiSupport.requireInternalKey(request);
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return ApiResponse.ok(toResponse(user));
    }

    @GetMapping
    public ApiResponse<List<InternalUserResponse>> getByIds(
            @RequestParam String ids,
            HttpServletRequest request) {
        internalApiSupport.requireInternalKey(request);
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
        List<InternalUserResponse> users = userRepository.findAllById(idList).stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(users);
    }

    @PutMapping("/{id}/mobile")
    public ApiResponse<InternalUserResponse> updateMobile(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        internalApiSupport.requireInternalKey(request);
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        String mobile = body == null ? null : body.get("mobile");
        if (mobile == null || mobile.isBlank()) {
            throw ApiException.badRequest("mobile is required");
        }
        user.setMobile(mobile.trim());
        return ApiResponse.ok(toResponse(userRepository.save(user)));
    }

    private InternalUserResponse toResponse(User user) {
        return new InternalUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getMobile(),
                user.getRole() == null ? null : user.getRole().name(),
                user.isActive());
    }
}
