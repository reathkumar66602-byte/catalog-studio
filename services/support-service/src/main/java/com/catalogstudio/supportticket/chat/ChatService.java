package com.catalogstudio.supportticket.chat;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.security.AuthUser;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.supportticket.SupportedLocales;
import com.catalogstudio.supportticket.ai.SupportAiClient;
import com.catalogstudio.supportticket.ai.SupportAiClientResolver;
import com.catalogstudio.supportticket.dto.ChatMessageRequest;
import com.catalogstudio.supportticket.dto.ChatMessageResponse;
import com.catalogstudio.supportticket.dto.ChatSessionResponse;
import com.catalogstudio.supportticket.dto.CreateChatSessionRequest;
import com.catalogstudio.supportticket.dto.UpdateChatLocaleRequest;
import com.catalogstudio.supportticket.entity.ChatMessageRole;
import com.catalogstudio.supportticket.entity.ChatSessionStatus;
import com.catalogstudio.supportticket.entity.SupportChatMessage;
import com.catalogstudio.supportticket.entity.SupportChatSession;
import com.catalogstudio.supportticket.knowledge.KnowledgeRetrievalService;
import com.catalogstudio.supportticket.repository.SupportChatMessageRepository;
import com.catalogstudio.supportticket.repository.SupportChatSessionRepository;
import com.catalogstudio.supportticket.service.SupportMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final SupportChatSessionRepository sessionRepository;
    private final SupportChatMessageRepository messageRepository;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final SupportAiClientResolver aiClientResolver;

    @Transactional
    public ChatSessionResponse createSession(CreateChatSessionRequest request) {
        AuthUser user = SecurityUtils.currentUser();
        SupportChatSession session = sessionRepository.save(SupportChatSession.builder()
                .customerUserId(user.id())
                .locale(SupportedLocales.normalize(request == null ? null : request.locale()))
                .status(ChatSessionStatus.ACTIVE)
                .build());
        return SupportMapper.toChatSession(session);
    }

    @Transactional
    public ChatMessageResponse postMessage(UUID sessionUuid, ChatMessageRequest request) {
        AuthUser user = SecurityUtils.currentUser();
        SupportChatSession session = sessionRepository
                .findByUuidAndCustomerUserId(sessionUuid, user.id())
                .orElseThrow(() -> ApiException.notFound("Chat session not found"));
        if (session.getStatus() == ChatSessionStatus.CLOSED) {
            throw ApiException.badRequest("Chat session is closed");
        }

        SupportChatMessage userMessage = messageRepository.save(SupportChatMessage.builder()
                .sessionId(session.getId())
                .role(ChatMessageRole.USER)
                .content(request.content().trim())
                .build());

        List<SupportChatMessage> history = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        List<SupportAiClient.ChatTurn> turns = history.stream()
                .filter(m -> m.getRole() != ChatMessageRole.SYSTEM)
                .filter(m -> !m.getId().equals(userMessage.getId()))
                .map(m -> new SupportAiClient.ChatTurn(
                        m.getRole() == ChatMessageRole.ASSISTANT ? "assistant" : "user",
                        m.getContent()))
                .toList();

        String kb = knowledgeRetrievalService.buildContext(session.getLocale(), request.content());
        SupportAiClient.AiReply reply = aiClientResolver.reply(
                session.getLocale(), turns, request.content().trim(), kb);

        if (reply.suggestTicket()) {
            session.setStatus(ChatSessionStatus.ESCALATED);
            sessionRepository.save(session);
        }

        SupportChatMessage assistant = messageRepository.save(SupportChatMessage.builder()
                .sessionId(session.getId())
                .role(ChatMessageRole.ASSISTANT)
                .content(reply.content())
                .metadata(reply.suggestTicket() ? "{\"suggestTicket\":true}" : null)
                .build());
        return SupportMapper.toChatMessage(assistant, reply.suggestTicket());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(UUID sessionUuid) {
        AuthUser user = SecurityUtils.currentUser();
        SupportChatSession session = sessionRepository
                .findByUuidAndCustomerUserId(sessionUuid, user.id())
                .orElseThrow(() -> ApiException.notFound("Chat session not found"));
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(m -> SupportMapper.toChatMessage(
                        m, m.getMetadata() != null && m.getMetadata().contains("suggestTicket")))
                .toList();
    }

    @Transactional
    public ChatSessionResponse updateLocale(UUID sessionUuid, UpdateChatLocaleRequest request) {
        AuthUser user = SecurityUtils.currentUser();
        SupportChatSession session = sessionRepository
                .findByUuidAndCustomerUserId(sessionUuid, user.id())
                .orElseThrow(() -> ApiException.notFound("Chat session not found"));
        session.setLocale(SupportedLocales.normalize(request.locale()));
        return SupportMapper.toChatSession(sessionRepository.save(session));
    }
}
