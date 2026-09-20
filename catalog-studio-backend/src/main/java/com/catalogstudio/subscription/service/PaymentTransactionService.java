package com.catalogstudio.subscription.service;

import com.catalogstudio.subscription.dto.TransactionHistoryItem;
import com.catalogstudio.subscription.entity.PaymentTransaction;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionStatus;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionType;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.repository.PaymentTransactionRepository;
import com.catalogstudio.user.entity.User;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentTransactionRepository transactionRepository;

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
        String planName = plan == null || plan.getName() == null ? "NONE" : plan.getName();
        BigDecimal amount = BigDecimal.ZERO;
        if (type != TransactionType.TRIAL && plan != null && plan.getPrice() != null) {
            amount = plan.getPrice();
        }
        return transactionRepository.save(PaymentTransaction.builder()
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
                .build());
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
}
