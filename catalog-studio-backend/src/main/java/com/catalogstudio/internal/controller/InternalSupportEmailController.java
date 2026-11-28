package com.catalogstudio.internal.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.events.support.SupportEmailNotificationEvent;
import com.catalogstudio.internal.InternalApiSupport;
import com.catalogstudio.internal.messaging.SupportEmailDispatch;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/support/emails")
@RequiredArgsConstructor
public class InternalSupportEmailController {

    private final InternalApiSupport internalApiSupport;
    private final SupportEmailDispatch supportEmailDispatch;

    @PostMapping
    public ApiResponse<Boolean> send(
            @RequestBody SupportEmailNotificationEvent body,
            HttpServletRequest request) {
        internalApiSupport.requireInternalKey(request);
        boolean sent = supportEmailDispatch.dispatch(body);
        return ApiResponse.ok(sent);
    }
}
