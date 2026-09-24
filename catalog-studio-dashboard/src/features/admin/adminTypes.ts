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
