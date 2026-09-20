package com.catalogstudio.user.dto;

import com.catalogstudio.subscription.dto.TransactionHistoryItem;
import java.util.List;

public record DashboardResponse(
        long totalProducts,
        long aiAnalysesThisMonth,
        long savedTemplates,
        long autofillProfiles,
        long connectedMarketplaces,
        long extensionDevices,
        String currentPlan,
        String planStatus,
        boolean accessEntitled,
        boolean requiresRecharge,
        boolean trialActive,
        long daysRemaining,
        String trialEndsOn,
        long monthlyAiLimit,
        long monthlyAiRemaining,
        long transactionCount,
        List<TransactionHistoryItem> recentTransactions
) {}
