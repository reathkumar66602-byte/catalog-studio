import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "./store/auth";
import { LanguageProvider } from "./i18n/LanguageProvider";
import { ProtectedRoute } from "./routes/ProtectedRoute";
import { AdminRoute } from "./routes/AdminRoute";
import { AppLayout } from "./layout/AppLayout";
import { LoginPage } from "./features/auth/LoginPage";
import { RegisterPage } from "./features/auth/RegisterPage";
import { DashboardPage } from "./pages/DashboardPage";
import { AnalyzePage } from "./features/analysis/AnalyzePage";
import { ReviewPage } from "./features/analysis/ReviewPage";
import { ProductsPage } from "./features/products/ProductsPage";
import { TemplatesPage, ProfilesPage } from "./features/templates/TemplatesPage";
import { MarketplacesPage } from "./features/marketplaces/MarketplacesPage";
import { AnalysisHistoryPage } from "./features/analysis/AnalysisHistoryPage";
import { ExtensionPage } from "./features/extension/ExtensionPage";
import { ExtensionBridge } from "./features/extension/ExtensionBridge";
import { SubscriptionPage } from "./features/subscription/SubscriptionPage";
import { SettingsPage } from "./features/settings/SettingsPage";
import { BillingAddressPage } from "./features/settings/BillingAddressPage";
import { EmailTemplatesPage } from "./features/settings/EmailTemplatesPage";
import { LabelsHubPage } from "./features/labels/LabelsHubPage";
import { MergePdfPage } from "./features/labels/MergePdfPage";
import { MeeshoCropPage } from "./features/labels/MeeshoCropPage";
import { FlipkartCropPage } from "./features/labels/FlipkartCropPage";
import { MeeshoCalculatorPage } from "./features/calculator/MeeshoCalculatorPage";
import { useTheme } from "./hooks/useTheme";
import { SiteProvider } from "./features/site/useSite";
import { MarketingLayout } from "./features/site/MarketingLayout";
import { LandingPage } from "./features/site/LandingPage";
import { ContactPage, ExtensionMarketingPage, PrivacyPolicyPage } from "./features/site/ExtensionMarketingPage";
import { SiteAdminPage } from "./features/admin/SiteAdminPage";
import { BillingAdminPage } from "./features/admin/BillingAdminPage";
import { EntitlementGate } from "./routes/EntitlementGate";
import { GuestToolRedirect } from "./routes/GuestToolRedirect";

const queryClient = new QueryClient();

export default function App() {
  useTheme();
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <LanguageProvider>
        <SiteProvider>
          <ExtensionBridge />
          <BrowserRouter>
            <Routes>
              <Route element={<MarketingLayout />}>
                <Route path="/" element={<LandingPage />} />
                <Route path="/shipping-label-crop" element={<GuestToolRedirect to="/tools/labels/flipkart" />} />
                <Route path="/meesho-shipping-label-crop" element={<GuestToolRedirect to="/tools/labels/meesho" />} />
                <Route path="/merge-pdf" element={<GuestToolRedirect to="/tools/labels/merge" />} />
                <Route path="/calculator" element={<GuestToolRedirect to="/tools/meesho-calculator" />} />
                <Route path="/chrome-extension" element={<ExtensionMarketingPage />} />
                <Route path="/privacy" element={<PrivacyPolicyPage />} />
                <Route path="/contact" element={<ContactPage />} />
              </Route>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route element={<ProtectedRoute />}>
                <Route element={<EntitlementGate />}>
                  <Route element={<AppLayout />}>
                  <Route path="/dashboard" element={<DashboardPage />} />
                  <Route path="/products/analyze" element={<AnalyzePage />} />
                  <Route path="/products/:id/review" element={<ReviewPage />} />
                  <Route path="/products" element={<ProductsPage />} />
                  <Route path="/templates" element={<TemplatesPage />} />
                  <Route path="/profiles" element={<ProfilesPage />} />
                  <Route path="/marketplaces" element={<MarketplacesPage />} />
                  <Route path="/analysis" element={<AnalysisHistoryPage />} />
                  <Route path="/tools/labels" element={<LabelsHubPage />} />
                  <Route path="/tools/labels/merge" element={<MergePdfPage />} />
                  <Route path="/tools/labels/meesho" element={<MeeshoCropPage />} />
                  <Route path="/tools/labels/flipkart" element={<FlipkartCropPage />} />
                  <Route path="/tools/meesho-calculator" element={<MeeshoCalculatorPage />} />
                  <Route path="/billing-address" element={<BillingAddressPage />} />
                  <Route path="/extension" element={<ExtensionPage />} />
                  <Route path="/subscription" element={<SubscriptionPage />} />
                  <Route path="/settings" element={<SettingsPage />} />
                  <Route element={<AdminRoute />}>
                    <Route path="/admin/site" element={<SiteAdminPage />} />
                    <Route path="/admin/billing" element={<BillingAdminPage />} />
                    <Route path="/settings/email-templates" element={<EmailTemplatesPage />} />
                  </Route>
                  </Route>
                </Route>
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </SiteProvider>
        </LanguageProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
}
