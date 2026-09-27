export type AdminUserRow = {
  id: string;
  name: string;
  email: string;
  mobile?: string;
  username?: string;
  role: string;
  displayRole: string;
  status: string;
  plan?: string;
  planStatus?: string;
  accessEntitled: boolean;
  subscriptionStartDate?: string;
  subscriptionEndDate?: string;
  createdAt: string;
  features: Record<string, boolean>;
  pendingPlan?: string | null;
  pendingPromoCode?: string | null;
  promoApplied?: boolean;
  pendingBaseAmount?: number | null;
  pendingDiscountAmount?: number | null;
  pendingServiceCharge?: number | null;
  pendingGstAmount?: number | null;
  pendingTotalAmount?: number | null;
};

export type FeatureDefinition = {
  key: string;
  label: string;
  description: string;
  path: string;
  required?: boolean;
};

export type PageResult<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export type PlanOption = {
  id: string;
  name: string;
  price: number;
  billingCycle: string;
  purchasable?: boolean;
};
