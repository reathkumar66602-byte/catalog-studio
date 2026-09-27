package com.catalogstudio.subscription.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.subscription.dto.PriceBreakdown;
import com.catalogstudio.subscription.dto.TransactionHistoryItem;
import com.catalogstudio.subscription.entity.BillingSettings;
import com.catalogstudio.subscription.entity.PaymentTransaction;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionStatus;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionType;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.repository.PaymentTransactionRepository;
import com.catalogstudio.user.entity.User;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private static final DateTimeFormatter INVOICE_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PaymentTransactionRepository transactionRepository;
    private final BillingSettingsService billingSettingsService;

    @Transactional
    public PaymentTransaction record(
            User user,
            SubscriptionPlan plan,
            TransactionType type,
            TransactionStatus status,
            String provider,
            String reference,
            String notes
    ) {
        return record(user, plan, type, status, provider, reference, notes, null);
    }

    @Transactional
    public PaymentTransaction record(
            User user,
            SubscriptionPlan plan,
            TransactionType type,
            TransactionStatus status,
            String provider,
            String reference,
            String notes,
            PriceBreakdown pricing
    ) {
        String planName = plan == null || plan.getName() == null ? "NONE" : plan.getName();
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal service = BigDecimal.ZERO;
        BigDecimal gst = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        String promo = null;
        String gstin = null;
        if (pricing != null) {
            amount = nz(pricing.totalAmount());
            base = nz(pricing.baseAmount());
            service = nz(pricing.serviceCharge());
            gst = nz(pricing.gstAmount());
            discount = nz(pricing.discountAmount());
            promo = pricing.promoCode();
            gstin = pricing.companyGstin();
        } else if (type != TransactionType.TRIAL && plan != null && plan.getPrice() != null) {
            amount = plan.getPrice();
            base = plan.getPrice();
            BillingSettings billing = billingSettingsService.current();
            gstin = billing.getCompanyGstin();
        }
        PaymentTransaction saved = transactionRepository.save(PaymentTransaction.builder()
                .user(user)
                .plan(plan)
                .planName(planName)
                .amount(amount)
                .currency("INR")
                .provider(provider == null || provider.isBlank() ? "MANUAL" : provider)
                .reference(reference)
                .type(type)
                .status(status)
                .billingCycle(plan == null ? null : plan.getBillingCycle())
                .notes(notes)
                .baseAmount(base)
                .serviceCharge(service)
                .gstAmount(gst)
                .discountAmount(discount)
                .promoCode(promo)
                .companyGstin(gstin)
                .build());
        if (amount.signum() > 0 || type == TransactionType.ACTIVATION) {
            saved.setInvoiceNumber(buildInvoiceNumber(saved));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<TransactionHistoryItem> history(Long userId, Pageable pageable) {
        return transactionRepository.findByUser_Id(userId, pageable).map(TransactionHistoryItem::from);
    }

    @Transactional(readOnly = true)
    public List<TransactionHistoryItem> recent(Long userId) {
        return transactionRepository.findTop5ByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(TransactionHistoryItem::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long count(Long userId) {
        return transactionRepository.countByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public PaymentTransaction requireOwned(Long userId, UUID transactionId) {
        return transactionRepository.findByUuidAndUser_Id(transactionId, userId)
                .orElseThrow(() -> ApiException.notFound("Transaction not found"));
    }

    @Transactional(readOnly = true)
    public String renderBillHtml(Long userId, UUID transactionId) {
        PaymentTransaction txn = requireOwned(userId, transactionId);
        BillingSettings billing = billingSettingsService.current();
        String legal = StringUtils.hasText(billing.getCompanyLegalName())
                ? billing.getCompanyLegalName()
                : "Catalog Studio";
        String parent = StringUtils.hasText(billing.getParentCompanyName())
                ? billing.getParentCompanyName()
                : "Shirtaji";
        String gstin = StringUtils.hasText(txn.getCompanyGstin())
                ? txn.getCompanyGstin()
                : billing.getCompanyGstin();
        String invoice = StringUtils.hasText(txn.getInvoiceNumber())
                ? txn.getInvoiceNumber()
                : "CS-" + txn.getId();
        String promoLine = StringUtils.hasText(txn.getPromoCode())
                ? "<tr><td>Promo (" + esc(txn.getPromoCode()) + ")</td><td class=\"neg\">-₹"
                + money(txn.getDiscountAmount()) + "</td></tr>"
                : (nz(txn.getDiscountAmount()).signum() > 0
                ? "<tr><td>Discount</td><td class=\"neg\">-₹" + money(txn.getDiscountAmount()) + "</td></tr>"
                : "");
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <title>Invoice %s</title>
                  <style>
                    body{font-family:Segoe UI,Arial,sans-serif;color:#0f172a;margin:32px;background:#f8fafc}
                    .card{max-width:720px;margin:0 auto;background:#fff;border:1px solid #e2e8f0;border-radius:16px;padding:28px}
                    h1{margin:0;font-size:22px} .muted{color:#64748b;font-size:13px}
                    table{width:100%%;border-collapse:collapse;margin-top:20px}
                    td{padding:8px 0;border-bottom:1px solid #f1f5f9;font-size:14px}
                    td:last-child{text-align:right;font-variant-numeric:tabular-nums}
                    .neg{color:#b45309} .total{font-weight:700;font-size:16px}
                    .badge{display:inline-block;margin-top:8px;padding:4px 10px;border-radius:999px;background:#ecfdf5;color:#0f766e;font-size:12px}
                    @media print{body{margin:0;background:#fff}.card{border:none}}
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>%s</h1>
                    <p class="muted">A %s company · Tax invoice / payment receipt</p>
                    %s
                    <p class="badge">%s · %s</p>
                    <table>
                      <tr><td>Invoice</td><td>%s</td></tr>
                      <tr><td>Date</td><td>%s</td></tr>
                      <tr><td>Billed to</td><td>%s</td></tr>
                      <tr><td>Plan</td><td>%s (%s)</td></tr>
                      <tr><td>Plan amount</td><td>₹%s</td></tr>
                      %s
                      <tr><td>Service charge</td><td>₹%s</td></tr>
                      <tr><td>GST</td><td>₹%s</td></tr>
                      <tr><td class="total">Total paid</td><td class="total">₹%s</td></tr>
                      <tr><td>Reference</td><td>%s</td></tr>
                    </table>
                    <p class="muted" style="margin-top:24px">Thank you for subscribing to Catalog Studio.</p>
                  </div>
                  <script>window.onload=function(){window.print()}</script>
                </body>
                </html>
                """.formatted(
                esc(invoice),
                esc(legal),
                esc(parent),
                StringUtils.hasText(gstin) ? "<p class=\"muted\">GSTIN: " + esc(gstin) + "</p>" : "",
                esc(txn.getType().name()),
                esc(txn.getStatus().name()),
                esc(invoice),
                esc(String.valueOf(txn.getCreatedAt())),
                esc(txn.getUser() == null ? "" : txn.getUser().getEmail()),
                esc(txn.getPlanName()),
                esc(txn.getBillingCycle() == null ? "MONTHLY" : txn.getBillingCycle()),
                money(txn.getBaseAmount() != null ? txn.getBaseAmount() : txn.getAmount()),
                promoLine,
                money(txn.getServiceCharge()),
                money(txn.getGstAmount()),
                money(txn.getAmount()),
                esc(txn.getReference() == null ? "—" : txn.getReference())
        );
    }

    private String buildInvoiceNumber(PaymentTransaction saved) {
        String day = saved.getCreatedAt() == null
                ? INVOICE_DAY.format(java.time.LocalDate.now())
                : INVOICE_DAY.format(saved.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate());
        return "CS-" + day + "-" + saved.getId();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String money(BigDecimal value) {
        return nz(value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
