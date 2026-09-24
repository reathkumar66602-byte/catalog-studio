import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "./store/auth";
import { LanguageProvider } from "./i18n/LanguageProvider";
import { ProtectedRoute } from "./routes/ProtectedRoute";
import { AdminRoute } from "./routes/AdminRoute";
import { SuperAdminRoute } from "./routes/SuperAdminRoute";
import { FeatureRoute } from "./routes/FeatureRoute";
import { AppLayout } from "./layout/AppLayout";
import { LoginPage } from "./features/auth/LoginPage";
import { RegisterPage } from "./features/auth/RegisterPage";
import { ForgotPasswordPage } from "./features/auth/ForgotPasswordPage";
import { ResetPasswordPage } from "./features/auth/ResetPasswordPage";
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
import { ShootPage } from "./features/shoot/ShootPage";
import { TrendingPage } from "./features/trending/TrendingPage";
import { useTheme } from "./hooks/useTheme";
import { SiteProvider } from "./features/site/useSite";
import { MarketingLayout } from "./features/site/MarketingLayout";
import { LandingPage } from "./features/site/LandingPage";
import { ContactPage, ExtensionMarketingPage, PrivacyPolicyPage } from "./features/site/ExtensionMarketingPage";
import { SiteAdminPage } from "./features/admin/SiteAdminPage";
import { BillingAdminPage } from "./features/admin/BillingAdminPage";
import { UsersAdminPage } from "./features/admin/UsersAdminPage";
import { AccessAdminPage } from "./features/admin/AccessAdminPage";
import { StaffAdminPage } from "./features/admin/StaffAdminPage";
import { TransactionHistoryPage } from "./features/subscription/TransactionHistoryPage";
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
              <Route path="/forgot-password" element={<ForgotPasswordPage />} />
              <Route path="/reset-password" element={<ResetPasswordPage />} />
              <Route element={<ProtectedRoute />}>
                <Route element={<EntitlementGate />}>
                  <Route element={<AppLayout />}>
                  <Route path="/dashboard" element={<DashboardPage />} />
                  <Route element={<FeatureRoute feature="trending" />}>
                    <Route path="/trending" element={<TrendingPage />} />
                  </Route>
                  <Route element={<FeatureRoute feature="shoot" />}>
                    <Route path="/shoot" element={<ShootPage />} />
                  </Route>
                  <Route path="/products/analyze" element={<AnalyzePage />} />
                  <Route path="/products/:id/review" element={<ReviewPage />} />
                  <Route path="/products" element={<ProductsPage />} />
                  <Route path="/templates" element={<TemplatesPage />} />
                  <Route path="/profiles" element={<ProfilesPage />} />
                  <Route path="/marketplaces" element={<MarketplacesPage />} />
                  <Route element={<FeatureRoute feature="analysis" />}>
                    <Route path="/analysis" element={<AnalysisHistoryPage />} />
                  </Route>
                  <Route element={<FeatureRoute feature="labels" />}>
                    <Route path="/tools/labels" element={<LabelsHubPage />} />
                    <Route path="/tools/labels/merge" element={<MergePdfPage />} />
                    <Route path="/tools/labels/meesho" element={<MeeshoCropPage />} />
                    <Route path="/tools/labels/flipkart" element={<FlipkartCropPage />} />
                  </Route>
                  <Route element={<FeatureRoute feature="meesho_calculator" />}>
                    <Route path="/tools/meesho-calculator" element={<MeeshoCalculatorPage />} />
                  </Route>
                  <Route element={<FeatureRoute feature="billing_address" />}>
                    <Route path="/billing-address" element={<BillingAddressPage />} />
                  </Route>
                  <Route element={<FeatureRoute feature="extension" />}>
                    <Route path="/extension" element={<ExtensionPage />} />
                  </Route>
                  <Route element={<FeatureRoute feature="subscription" />}>
                    <Route path="/subscription" element={<SubscriptionPage />} />
                  </Route>
                  <Route element={<FeatureRoute feature="transactions" />}>
                    <Route path="/transactions" element={<TransactionHistoryPage />} />
                  </Route>
                  <Route element={<FeatureRoute feature="settings" />}>
                    <Route path="/settings" element={<SettingsPage />} />
                  </Route>
                  <Route element={<AdminRoute />}>
                    <Route path="/admin/users" element={<UsersAdminPage />} />
                    <Route path="/admin/access" element={<AccessAdminPage />} />
                    <Route path="/admin/site" element={<SiteAdminPage />} />
                    <Route path="/admin/billing" element={<BillingAdminPage />} />
                    <Route path="/settings/email-templates" element={<EmailTemplatesPage />} />
                  </Route>
                  <Route element={<SuperAdminRoute />}>
                    <Route path="/admin/staff" element={<StaffAdminPage />} />
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
