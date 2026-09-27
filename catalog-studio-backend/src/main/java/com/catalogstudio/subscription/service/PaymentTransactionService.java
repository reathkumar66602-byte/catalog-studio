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
                : "Shritaji";
        String gstin = StringUtils.hasText(txn.getCompanyGstin())
                ? txn.getCompanyGstin()
                : (StringUtils.hasText(billing.getCompanyGstin()) ? billing.getCompanyGstin() : "19CMZPM0096H1ZA");
        String invoice = StringUtils.hasText(txn.getInvoiceNumber())
                ? txn.getInvoiceNumber()
                : "CS-" + txn.getId();
        String promoLine = StringUtils.hasText(txn.getPromoCode())
                ? "<tr><td>Promo (" + esc(txn.getPromoCode()) + ")</td><td class=\"neg\">-₹"
                + money(txn.getDiscountAmount()) + "</td></tr>"
                : (nz(txn.getDiscountAmount()).signum() > 0
                ? "<tr><td>Discount</td><td class=\"neg\">-₹" + money(txn.getDiscountAmount()) + "</td></tr>"
                : "");
        String serviceLine = nz(txn.getServiceCharge()).signum() > 0
                ? "<tr><td>Service charge</td><td>₹" + money(txn.getServiceCharge()) + "</td></tr>"
                : "";
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <title>Tax Invoice %s</title>
                  <style>
                    body{font-family:Segoe UI,Arial,sans-serif;color:#0f172a;margin:32px;background:#f8fafc}
                    .card{max-width:720px;margin:0 auto;background:#fff;border:1px solid #e2e8f0;border-radius:16px;padding:28px}
                    .head{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}
                    h1{margin:0;font-size:22px} .muted{color:#64748b;font-size:13px}
                    .tag{display:inline-block;margin-top:6px;padding:3px 8px;border-radius:6px;background:#f1f5f9;color:#334155;font-size:11px;font-weight:600;letter-spacing:.04em}
                    table{width:100%%;border-collapse:collapse;margin-top:20px}
                    td{padding:8px 0;border-bottom:1px solid #f1f5f9;font-size:14px}
                    td:last-child{text-align:right;font-variant-numeric:tabular-nums}
                    .neg{color:#b45309} .total{font-weight:700;font-size:16px}
                    .badge{display:inline-block;margin-top:8px;padding:4px 10px;border-radius:999px;background:#ecfdf5;color:#0f766e;font-size:12px}
                    .foot{margin-top:28px;padding-top:16px;border-top:1px solid #e2e8f0}
                    @media print{body{margin:0;background:#fff}.card{border:none}}
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="head">
                      <div>
                        <h1>%s</h1>
                        <p class="muted" style="margin:6px 0 0">A product of %s</p>
                        <span class="tag">TAX INVOICE</span>
                      </div>
                      <div style="text-align:right">
                        <p class="muted" style="margin:0">GSTIN</p>
                        <p style="margin:4px 0 0;font-weight:600;letter-spacing:.02em">%s</p>
                      </div>
                    </div>
                    <p class="badge">%s · %s</p>
                    <table>
                      <tr><td>Invoice number</td><td>%s</td></tr>
                      <tr><td>Invoice date</td><td>%s</td></tr>
                      <tr><td>Billed to</td><td>%s</td></tr>
                      <tr><td>Description</td><td>%s (%s)</td></tr>
                      <tr><td>Taxable value</td><td>₹%s</td></tr>
                      %s
                      %s
                      <tr><td>GST</td><td>₹%s</td></tr>
                      <tr><td class="total">Total amount</td><td class="total">₹%s</td></tr>
                      <tr><td>Payment reference</td><td>%s</td></tr>
                    </table>
                    <div class="foot">
                      <p class="muted" style="margin:0">Issued by %s. Parent company: %s.</p>
                      <p class="muted" style="margin:8px 0 0">This is a computer-generated tax invoice / payment receipt for Catalog Studio subscription services.</p>
                    </div>
                  </div>
                  <script>window.onload=function(){window.print()}</script>
                </body>
                </html>
                """.formatted(
                esc(invoice),
                esc(legal),
                esc(parent),
                esc(gstin),
                esc(txn.getType().name()),
                esc(txn.getStatus().name()),
                esc(invoice),
                esc(String.valueOf(txn.getCreatedAt())),
                esc(txn.getUser() == null ? "" : txn.getUser().getEmail()),
                esc(txn.getPlanName()),
                esc(txn.getBillingCycle() == null ? "MONTHLY" : txn.getBillingCycle()),
                money(txn.getBaseAmount() != null ? txn.getBaseAmount() : txn.getAmount()),
                promoLine,
                serviceLine,
                money(txn.getGstAmount()),
                money(txn.getAmount()),
                esc(txn.getReference() == null ? "—" : txn.getReference()),
                esc(legal),
                esc(parent)
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
