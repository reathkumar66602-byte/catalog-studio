export type ApiResponse<T> = {
  success: boolean;
  message: string;
  data: T;
  errors?: { field: string; message: string }[];
  traceId?: string;
};

export type UserSummary = {
  id: string;
  name: string;
  email: string;
  role: string;
  emailVerified: boolean;
  businessName?: string;
  plan?: string;
  planStatus?: string;
  accessEntitled?: boolean;
  requiresRecharge?: boolean;
  trialEndsOn?: string;
  daysRemaining?: number;
  preferredLocale?: string;
};

export type AuthPayload = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserSummary;
};

export type AuthFlow = {
  otpRequired: boolean;
  challengeId?: string;
  maskedEmail?: string;
  otpExpiresInSeconds?: number;
  resendAfterSeconds?: number;
  purpose?: string;
  session?: AuthPayload;
};

export type Product = {
  id: string;
  name: string;
  productType?: string;
  category?: string;
  subcategory?: string;
  gender?: string;
  ageGroup?: string;
  primaryColor?: string;
  pattern?: string;
  material?: string;
  sleeveType?: string;
  neckType?: string;
  collarType?: string;
  fit?: string;
  occasion?: string;
  style?: string;
  description?: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  images: { id?: string; url: string; order: number; primary: boolean }[];
  attributes?: { name: string; value: string; confidence?: number; source: string }[];
  titles?: { id: string; title: string; selected: boolean; source: string }[];
};

export type AnalysisResult = {
  analysisId: string;
  productId: string;
  status: string;
  provider: string;
  model: string;
  overallConfidence?: number;
  createdAt: string;
  product: {
    productType?: string;
    category?: string;
    subCategory?: string;
    gender?: string;
    ageGroup?: string;
    primaryColor?: string;
    secondaryColors?: string[];
    colorConfidence?: number;
    pattern?: string;
    patternConfidence?: number;
    material?: string;
    materialConfidence?: number;
    sleeveType?: string;
    neckType?: string;
    collarType?: string;
    fit?: string;
    occasion?: string;
    style?: string;
    productDescription?: string;
    suggestedTitles?: string[];
    keywords?: string[];
    overallConfidence?: number;
    uncertainFields?: string[];
    images?: { id: string; url: string; order: number; primary: boolean }[];
  };
};

export type PlanCard = {
  id: string;
  name: string;
  price: number;
  billingCycle: string;
  features: Record<string, unknown>;
  status: string;
  purchasable: boolean;
};

export type SubscriptionStatus = {
  plan: string;
  status: string;
  effectiveStatus: string;
  startDate: string;
  endDate: string;
  accessEntitled: boolean;
  requiresRecharge: boolean;
  trialActive: boolean;
  daysRemaining: number;
  features: Record<string, unknown>;
  registeredEmail: string;
  trialDaysConfigured: number;
  rechargeHeadline: string;
  rechargeBody: string;
  pendingPlan?: string | null;
  paymentNotice?: {
    paymentProvider?: string;
    whatsappNumber: string;
    upiId: string;
    payeeName: string;
    qrImageUrl?: string | null;
    instructions: string;
  };
};

export type PaymentCheckout = {
  provider: string;
  checkoutUrl: string;
  sessionId: string;
  plan: string;
  amount: number;
  billingCycle: string;
  registeredEmail: string;
  upiId: string;
  payeeName: string;
  qrImageUrl?: string | null;
  qrImageDataUrl?: string | null;
  whatsappNumber: string;
  whatsappUrl: string;
  whatsappMessage: string;
  notice: string;
  instructions: string;
};

export type BillingSettings = {
  trialDays: number;
  trialPlan: string;
  whatsappNumber: string;
  whatsappMessageTemplate: string;
  upiId: string;
  payeeName: string;
  qrImageUrl?: string | null;
  paymentProvider?: string;
  paymentInstructions: string;
  rechargeHeadline: string;
  rechargeBody: string;
};

export type AccountProfile = {
  name?: string;
  username?: string;
  email?: string;
  mobile?: string;
  businessName?: string;
  gstNumber?: string;
  address?: string;
  preferredAiProvider?: string;
  preferredLocale?: string;
  plan?: string;
  planStatus?: string;
  emailVerified?: boolean;
  accessEntitled?: boolean;
  requiresRecharge?: boolean;
};

export type UserSessionRow = {
  id: string;
  deviceName?: string;
  ipAddress?: string;
  lastUsedAt?: string;
};

export type TransactionRow = {
  id: string;
  createdAt?: string;
  plan?: string;
  type?: string;
  status?: string;
  amount?: number;
  currency?: string;
  provider?: string;
  reference?: string;
};


