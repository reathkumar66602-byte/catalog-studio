package com.catalogstudio.supportticket.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.supportticket.chat.ChatService;
import com.catalogstudio.supportticket.dto.ChatMessageRequest;
import com.catalogstudio.supportticket.dto.ChatMessageResponse;
import com.catalogstudio.supportticket.dto.ChatSessionResponse;
import com.catalogstudio.supportticket.dto.CreateChatSessionRequest;
import com.catalogstudio.supportticket.dto.UpdateChatLocaleRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support/chat")
@RequiredArgsConstructor
public class SupportChatController {

    private final ChatService chatService;

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(@Valid @RequestBody(required = false) CreateChatSessionRequest request) {
        return ApiResponse.ok(chatService.createSession(request == null ? new CreateChatSessionRequest(null) : request));
    }

    @PostMapping("/sessions/{uuid}/messages")
    public ApiResponse<ChatMessageResponse> postMessage(
            @PathVariable UUID uuid,
            @Valid @RequestBody ChatMessageRequest request) {
        return ApiResponse.ok(chatService.postMessage(uuid, request));
    }

    @GetMapping("/sessions/{uuid}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(@PathVariable UUID uuid) {
        return ApiResponse.ok(chatService.listMessages(uuid));
    }

    @PatchMapping("/sessions/{uuid}/locale")
    public ApiResponse<ChatSessionResponse> updateLocale(
            @PathVariable UUID uuid,
            @Valid @RequestBody UpdateChatLocaleRequest request) {
        return ApiResponse.ok(chatService.updateLocale(uuid, request));
    }
}
