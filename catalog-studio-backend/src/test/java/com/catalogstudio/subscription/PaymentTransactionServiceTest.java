package com.catalogstudio.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catalogstudio.subscription.entity.PaymentTransaction;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionStatus;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionType;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.repository.PaymentTransactionRepository;
import com.catalogstudio.subscription.service.PaymentTransactionService;
import com.catalogstudio.user.entity.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceTest {

    @Mock PaymentTransactionRepository transactionRepository;
    @InjectMocks PaymentTransactionService paymentTransactionService;

    @Test
    void trialIsRecordedAtZeroAmount() {
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        User user = User.builder().id(11L).email("seller@example.com").build();
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("PRO")
                .price(new BigDecimal("499"))
                .billingCycle("MONTHLY")
                .build();

        PaymentTransaction saved = paymentTransactionService.record(
                user, plan, TransactionType.TRIAL, TransactionStatus.SUCCESS, "MANUAL", "ref-1", "Signup trial");

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getPlanName()).isEqualTo("PRO");
        assertThat(saved.getType()).isEqualTo(TransactionType.TRIAL);
    }
}
