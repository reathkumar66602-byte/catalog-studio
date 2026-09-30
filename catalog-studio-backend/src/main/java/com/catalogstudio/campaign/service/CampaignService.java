package com.catalogstudio.campaign.service;

import com.catalogstudio.campaign.dto.CampaignRunResponse;
import com.catalogstudio.campaign.dto.CampaignTriggerRequest;
import com.catalogstudio.campaign.dto.NotificationTemplateResponse;
import com.catalogstudio.campaign.entity.CampaignRun;
import com.catalogstudio.campaign.entity.CampaignRun.DeliveryChannel;
import com.catalogstudio.campaign.entity.CampaignRun.RunStatus;
import com.catalogstudio.campaign.entity.CampaignRunItem;
import com.catalogstudio.campaign.entity.CampaignRunItem.ItemStatus;
import com.catalogstudio.campaign.entity.NotificationTemplate;
import com.catalogstudio.campaign.entity.NotificationTemplate.CampaignType;
import com.catalogstudio.campaign.entity.NotificationTemplate.Channel;
import com.catalogstudio.campaign.repository.CampaignRunItemRepository;
import com.catalogstudio.campaign.repository.CampaignRunRepository;
import com.catalogstudio.campaign.repository.NotificationTemplateRepository;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.email.service.EmailTemplateRenderer;
import com.catalogstudio.email.service.MailDispatchService;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.subscription.entity.BillingPromoCode;
import com.catalogstudio.subscription.entity.Subscription;
import com.catalogstudio.subscription.entity.Subscription.SubscriptionStatus;
import com.catalogstudio.subscription.repository.BillingPromoCodeRepository;
import com.catalogstudio.subscription.repository.SubscriptionRepository;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private static final Set<Long> EXPIRING_DAYS = Set.of(0L, 1L, 3L, 4L, 5L);

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingPromoCodeRepository promoCodeRepository;
    private final NotificationTemplateRepository templateRepository;
    private final CampaignRunRepository runRepository;
    private final CampaignRunItemRepository itemRepository;
    private final MailDispatchService mailDispatchService;
    private final WhatsAppOutboundService whatsAppOutboundService;
    private final CatalogStudioProperties properties;

    @Transactional(readOnly = true)
    public List<NotificationTemplateResponse> templates() {
        return templateRepository.findAllByOrderByCampaignTypeAscChannelAsc().stream()
                .map(t -> new NotificationTemplateResponse(
                        t.getUuid(), t.getSlug(), t.getName(), t.getChannel().name(),
                        t.getCampaignType().name(), t.getSubject(), t.getBodyText(), t.isEnabled()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignRunResponse> recentRuns() {
        return runRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> capabilities() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("whatsappConfigured", whatsAppOutboundService.configured());
        map.put("whatsappMessage", whatsAppOutboundService.capabilityMessage());
        map.put("emailEnabled", properties.mail() == null || properties.mail().enabled());
        return map;
    }

    @Transactional
    public CampaignRunResponse trigger(CampaignTriggerRequest request) {
        User actor = userRepository.findById(SecurityUtils.currentUserId())
                .orElseThrow(() -> ApiException.unauthorized("Unauthorized"));
        if (!actor.isStaff()) {
            throw ApiException.forbidden("Only admin or super admin can trigger campaigns");
        }
        CampaignType type = parseType(request.campaignType());
        DeliveryChannel channel = parseChannel(request.channel());
        String promo = StringUtils.hasText(request.promoCode()) ? request.promoCode().trim().toUpperCase(Locale.ROOT) : null;
        if (promo != null) {
            BillingPromoCode code = promoCodeRepository.findByCodeIgnoreCase(promo)
                    .orElseThrow(() -> ApiException.badRequest("Promo code not found"));
            if (!"ACTIVE".equalsIgnoreCase(code.getStatus())) {
                throw ApiException.badRequest("Promo code is not active");
            }
        }

        List<User> audience = selectAudience(type);
        CampaignRun run = runRepository.save(CampaignRun.builder()
                .campaignType(type)
                .channel(channel)
                .promoCode(promo)
                .triggeredBy(actor)
                .status(RunStatus.PENDING)
                .totalRecipients(audience.size())
                .notes(request.notes())
                .build());

        List<CampaignRunItem> items = new ArrayList<>();
        for (User user : audience) {
            items.add(CampaignRunItem.builder()
                    .campaignRun(run)
                    .user(user)
                    .email(user.getEmail())
                    .mobile(user.getMobile())
                    .status(ItemStatus.PENDING)
                    .build());
        }
        itemRepository.saveAll(items);
        log.info("Campaign {} queued by {} for {} recipients channel={}",
                type, actor.getEmail(), audience.size(), channel);
        return toResponse(run);
    }

    @Transactional
    public int processPendingBatch(int limit) {
        List<CampaignRunItem> pending = itemRepository.findByStatusOrderByIdAsc(
                ItemStatus.PENDING, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
        if (pending.isEmpty()) {
            return 0;
        }
        int processed = 0;
        Set<Long> runIds = new HashSet<>();
        for (CampaignRunItem item : pending) {
            CampaignRun run = item.getCampaignRun();
            if (run.getStatus() == RunStatus.PENDING) {
                run.setStatus(RunStatus.RUNNING);
                run.setStartedAt(java.time.Instant.now());
            }
            processItem(item, run);
            runIds.add(run.getId());
            processed++;
        }
        for (Long runId : runIds) {
            finalizeRunIfComplete(runId);
        }
        return processed;
    }

    private void processItem(CampaignRunItem item, CampaignRun run) {
        User user = item.getUser();
        if (user == null || user.getStatus() == User.UserStatus.DISABLED) {
            markSkipped(item, run, "User deactivated");
            return;
        }
        Map<String, String> vars = buildVariables(user, run);
        DeliveryChannel channel = run.getChannel();
        boolean emailWanted = channel == DeliveryChannel.EMAIL || channel == DeliveryChannel.BOTH;
        boolean waWanted = channel == DeliveryChannel.WHATSAPP || channel == DeliveryChannel.BOTH;

        boolean anySent = false;
        String lastSkip = null;
        String lastError = null;

        if (emailWanted) {
            String emailResult = sendEmail(run.getCampaignType(), user, vars);
            if (emailResult == null) {
                anySent = true;
            } else if (emailResult.startsWith("ERROR:")) {
                lastError = emailResult.substring(6);
            } else {
                lastSkip = emailResult;
            }
        }
        if (waWanted) {
            String waResult = sendWhatsApp(run.getCampaignType(), user, vars);
            if (waResult == null) {
                anySent = true;
            } else if (waResult.startsWith("ERROR:")) {
                lastError = waResult.substring(6);
            } else {
                lastSkip = waResult;
            }
        }

        if (anySent) {
            item.setStatus(ItemStatus.SENT);
            item.setSentAt(java.time.Instant.now());
            run.setSentCount(run.getSentCount() + 1);
        } else if (lastError != null) {
            item.setStatus(ItemStatus.FAILED);
            item.setErrorMessage(lastError);
            run.setFailedCount(run.getFailedCount() + 1);
        } else {
            markSkipped(item, run, lastSkip == null ? "Nothing to send" : lastSkip);
        }
    }

    private void markSkipped(CampaignRunItem item, CampaignRun run, String reason) {
        item.setStatus(ItemStatus.SKIPPED);
        item.setSkipReason(reason);
        run.setSkippedCount(run.getSkippedCount() + 1);
    }

    private String sendEmail(CampaignType type, User user, Map<String, String> vars) {
        if (!StringUtils.hasText(user.getEmail())) {
            return "User has no email";
        }
        if (properties.mail() != null && !properties.mail().enabled()) {
            return "Email sending is disabled";
        }
        NotificationTemplate template = templateRepository
                .findFirstByCampaignTypeAndChannelAndEnabledTrue(type, Channel.EMAIL)
                .orElse(null);
        if (template == null) {
            return "Email template missing for " + type;
        }
        try {
            String subject = EmailTemplateRenderer.render(nullToEmpty(template.getSubject()), vars);
            String html = EmailTemplateRenderer.render(
                    StringUtils.hasText(template.getBodyHtml()) ? template.getBodyHtml() : template.getBodyText(),
                    vars);
            String text = EmailTemplateRenderer.render(template.getBodyText(), vars);
            boolean sent = mailDispatchService.sendHtml(user.getEmail().trim(), subject, html, text);
            return sent ? null : "ERROR:Email provider did not accept message";
        } catch (Exception ex) {
            log.warn("Campaign email failed for {}: {}", user.getEmail(), ex.toString());
            return "ERROR:" + ex.getMessage();
        }
    }

    private String sendWhatsApp(CampaignType type, User user, Map<String, String> vars) {
        if (!StringUtils.hasText(user.getMobile())) {
            return "User has no mobile number";
        }
        if (!whatsAppOutboundService.configured()) {
            return "WhatsApp API not configured";
        }
        NotificationTemplate template = templateRepository
                .findFirstByCampaignTypeAndChannelAndEnabledTrue(type, Channel.WHATSAPP)
                .orElse(null);
        if (template == null) {
            return "WhatsApp template missing for " + type;
        }
        String body = EmailTemplateRenderer.render(template.getBodyText(), vars);
        String fail = whatsAppOutboundService.sendText(user.getMobile(), body);
        if (fail == null) {
            return null;
        }
        if (fail.contains("failed") || fail.contains("HTTP")) {
            return "ERROR:" + fail;
        }
        return fail;
    }

    private Map<String, String> buildVariables(User user, CampaignRun run) {
        Map<String, String> vars = new HashMap<>();
        vars.put("name", StringUtils.hasText(user.getName()) ? user.getName().trim() : "there");
        vars.put("email", nullToEmpty(user.getEmail()));
        vars.put("appName", "Catalog Studio");
        String origin = properties.cors() == null ? "https://catalogstudio.in" : properties.cors().publicAppOrigin();
        vars.put("loginLink", origin + "/subscription");
        vars.put("promoCode", nullToEmpty(run.getPromoCode()));
        if (StringUtils.hasText(run.getPromoCode())) {
            vars.put("promoLine", "Use promo code " + run.getPromoCode() + " at checkout. ");
        } else {
            vars.put("promoLine", "");
        }
        Subscription sub = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).orElse(null);
        if (sub != null) {
            vars.put("plan", sub.getPlan() == null ? "" : sub.getPlan().getName());
            vars.put("endDate", sub.getEndDate() == null ? "" : sub.getEndDate().toString());
            if (sub.getEndDate() != null) {
                long days = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), sub.getEndDate()));
                vars.put("daysLeft", Long.toString(days));
            } else {
                vars.put("daysLeft", "0");
            }
        } else {
            vars.put("plan", "");
            vars.put("endDate", "");
            vars.put("daysLeft", "0");
        }
        return vars;
    }

    private void finalizeRunIfComplete(Long runId) {
        CampaignRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        long pending = itemRepository.countByCampaignRunIdAndStatus(runId, ItemStatus.PENDING);
        if (pending > 0) {
            return;
        }
        run.setStatus(RunStatus.DONE);
        run.setFinishedAt(java.time.Instant.now());
    }

    private List<User> selectAudience(CampaignType type) {
        List<User> workspace = userRepository.findAll().stream()
                .filter(User::isWorkspaceUser)
                .filter(u -> u.getStatus() == User.UserStatus.ACTIVE)
                .toList();
        if (workspace.isEmpty()) {
            return List.of();
        }
        List<Long> ids = workspace.stream().map(User::getId).toList();
        Map<Long, Subscription> latest = new HashMap<>();
        for (Subscription s : subscriptionRepository.findLatestForUsers(ids)) {
            latest.put(s.getUser().getId(), s);
        }
        LocalDate today = LocalDate.now();
        List<User> matched = new ArrayList<>();
        for (User user : workspace) {
            Subscription sub = latest.get(user.getId());
            if (matches(type, sub, today)) {
                matched.add(user);
            }
        }
        return matched;
    }

    private boolean matches(CampaignType type, Subscription sub, LocalDate today) {
        return switch (type) {
            case NO_PURCHASE -> neverPurchased(sub, today);
            case TRIAL_EXPIRED -> trialExpired(sub, today);
            case EXPIRING_SOON -> expiringSoon(sub, today);
        };
    }

    /** No active paid window — trial/free/expired/never paid. */
    private boolean neverPurchased(Subscription sub, LocalDate today) {
        if (sub == null || sub.getPlan() == null) {
            return true;
        }
        boolean paidActive = sub.getStatus() == SubscriptionStatus.ACTIVE
                && (sub.getEndDate() == null || !today.isAfter(sub.getEndDate()))
                && isPaidPlan(sub);
        return !paidActive;
    }

    private boolean trialExpired(Subscription sub, LocalDate today) {
        if (sub == null) {
            return false;
        }
        if (sub.getStatus() != SubscriptionStatus.TRIAL) {
            // also catch trials that flipped to EXPIRED
            return sub.getStatus() == SubscriptionStatus.EXPIRED
                    && sub.getTrialStartedAt() != null
                    && !isPaidPlan(sub);
        }
        return sub.getEndDate() != null && today.isAfter(sub.getEndDate());
    }

    private boolean expiringSoon(Subscription sub, LocalDate today) {
        if (sub == null || sub.getPlan() == null || sub.getEndDate() == null) {
            return false;
        }
        if (sub.getStatus() != SubscriptionStatus.ACTIVE || !isPaidPlan(sub)) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(today, sub.getEndDate());
        return EXPIRING_DAYS.contains(days);
    }

    private static boolean isPaidPlan(Subscription sub) {
        if (sub.getPlan() == null) {
            return false;
        }
        String name = sub.getPlan().getName();
        if ("FREE".equalsIgnoreCase(name)) {
            return false;
        }
        return sub.getPlan().getPrice() != null && sub.getPlan().getPrice().signum() > 0;
    }

    private CampaignType parseType(String raw) {
        try {
            return CampaignType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw ApiException.badRequest("campaignType must be NO_PURCHASE, TRIAL_EXPIRED, or EXPIRING_SOON");
        }
    }

    private DeliveryChannel parseChannel(String raw) {
        try {
            return DeliveryChannel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw ApiException.badRequest("channel must be EMAIL, WHATSAPP, or BOTH");
        }
    }

    private CampaignRunResponse toResponse(CampaignRun run) {
        return new CampaignRunResponse(
                run.getUuid(),
                run.getCampaignType().name(),
                run.getChannel().name(),
                run.getPromoCode(),
                run.getStatus().name(),
                run.getTotalRecipients(),
                run.getSentCount(),
                run.getSkippedCount(),
                run.getFailedCount(),
                run.getNotes(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getFinishedAt(),
                whatsAppOutboundService.capabilityMessage()
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
