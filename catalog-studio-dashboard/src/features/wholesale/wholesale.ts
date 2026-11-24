import type { SubscriptionStatus } from "../../types";

export type WholesaleCategory = { id: string; name: string; slug: string; productCount: number };

/** AI suggestions from POST /wholesale/supplier/products/autofill */
export type ProductAutofill = {
  title?: string | null;
  description?: string | null;
  details?: string | null;
  categoryId?: string | null;
  categoryName?: string | null;
  primaryColor?: string | null;
  colorHex?: string | null;
  productType?: string | null;
  overallConfidence?: number | null;
};

export type PageResult<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export type ColorSwatch = { label: string; hex?: string | null };

export type ProductCard = {
  id: string;
  slug: string;
  title: string;
  code: string;
  status: string;
  primaryImageUrl?: string | null;
  minPrice?: number | null;
  maxPrice?: number | null;
  mrp?: number | null;
  colors: ColorSwatch[];
  totalStock: number;
  bookedCount: number;
  categoryName?: string | null;
  categorySlug?: string | null;
  storeId?: string | null;
  storeName?: string | null;
  storeSlug?: string | null;
};

export type VariantCell = {
  id: string;
  sku: string;
  colorLabel: string;
  sizeLabel: string;
  colorHex?: string | null;
  price: number;
  mrp?: number | null;
  available: number;
  onHand?: number | null;
  reserved?: number | null;
  status: string;
};

export type ProductDetail = {
  id: string;
  slug: string;
  title: string;
  code: string;
  status: string;
  description?: string | null;
  details?: string | null;
  mrp?: number | null;
  minPrice?: number | null;
  maxPrice?: number | null;
  totalStock: number;
  bookedCount: number;
  category: WholesaleCategory;
  store: {
    id: string;
    name: string;
    slug: string;
    city?: string | null;
    state?: string | null;
    phone?: string | null;
    email?: string | null;
  };
  images: { id: string; url: string; colorLabel?: string | null; sortOrder: number }[];
  colors: ColorSwatch[];
  sizes: string[];
  stockMatrix: Record<string, Record<string, number>>;
  variants: VariantCell[];
};

export type WholesaleStore = {
  id: string;
  storeName: string;
  slug: string;
  description?: string | null;
  city?: string | null;
  state?: string | null;
  addressLine?: string | null;
  pincode?: string | null;
  pan?: string | null;
  status: string;
  productCount: number;
  categories: WholesaleCategory[];
  gstin?: string | null;
  gstLegalName?: string | null;
  gstVerificationStatus?: string | null;
  gstVerifiedAt?: string | null;
  phone?: string | null;
  email?: string | null;
  gstPercent?: number | null;
  bankAccountName?: string | null;
  bankAccountNumber?: string | null;
  bankName?: string | null;
  bankIfsc?: string | null;
  bankBranch?: string | null;
  invoiceTerms?: string | null;
};

export type WholesaleCoupon = {
  id: string;
  code: string;
  discountType: "PERCENT" | "FIXED";
  discountValue: number;
  minOrder?: number | null;
  maxDiscount?: number | null;
  startsAt?: string | null;
  endsAt?: string | null;
  usageLimit?: number | null;
  usedCount: number;
  active: boolean;
};

export type OrderBillPreview = {
  subtotal: number;
  discountAmount: number;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  gstPercent: number;
  grandTotal: number;
  couponCode?: string | null;
};

export type OrderItem = {
  id: string;
  variantId?: string | null;
  productId?: string | null;
  productTitle: string;
  colorLabel?: string | null;
  sizeLabel?: string | null;
  sku?: string | null;
  hsnSac?: string | null;
  unitPrice: number;
  acceptedUnitPrice?: number | null;
  quantity: number;
  lineTotal: number;
};

export type WholesaleOrder = {
  id: string;
  orderNumber: string;
  status: "PLACED" | "ACCEPTED" | "REJECTED" | "CANCELLED" | "FULFILLED";
  source?: "ONLINE" | "MANUAL" | null;
  buyerNote?: string | null;
  supplierNote?: string | null;
  subtotal: number;
  discountAmount?: number | null;
  taxableAmount?: number | null;
  cgstAmount?: number | null;
  sgstAmount?: number | null;
  gstPercent?: number | null;
  grandTotal?: number | null;
  couponCode?: string | null;
  buyerName?: string | null;
  buyerEmail?: string | null;
  buyerMobile?: string | null;
  buyerGstin?: string | null;
  buyerAddress?: string | null;
  acceptedTotal?: number | null;
  currency: string;
  placedAt: string;
  acceptedAt?: string | null;
  rejectedAt?: string | null;
  billPdfUrl?: string | null;
  storeId?: string | null;
  storeName?: string | null;
  items: OrderItem[];
  supplierContact?: { phone?: string | null; email?: string | null } | null;
  buyer?: {
    name?: string | null;
    email?: string | null;
    mobile?: string | null;
    gstin?: string | null;
    address?: string | null;
  } | null;
};

export function formatInr(value?: number | null) {
  if (value === null || value === undefined) return "-";
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: value % 1 === 0 ? 0 : 2,
  }).format(value);
}

export function priceRange(min?: number | null, max?: number | null) {
  if (min === null || min === undefined) return "-";
  if (max === null || max === undefined || max === min) return formatInr(min);
  return `${formatInr(min)} - ${formatInr(max)}`;
}

/** Same rule as the backend: plan SUPPLIER or features.wholesaleSupplier = true. */
export function canSupply(access?: Pick<SubscriptionStatus, "plan" | "features" | "accessEntitled"> | null) {
  if (!access) return false;
  const plan = String(access.plan || "").toUpperCase();
  if (plan === "SUPPLIER" || plan.startsWith("SUPPLIER_")) return true;
  const flag = access.features?.wholesaleSupplier;
  return flag === true || flag === "true";
}

/** Backend also allows staff; UI mirrors that for Create store CTAs. */
export function canOpenSupplierStore(
  access?: Pick<SubscriptionStatus, "plan" | "features" | "accessEntitled"> | null,
  role?: string | null,
) {
  if (role === "ADMIN" || role === "SUPERADMIN") return true;
  return canSupply(access);
}

export const ORDER_STATUS_STYLE: Record<string, string> = {
  PLACED: "bg-amber-100 text-amber-800",
  ACCEPTED: "bg-teal-100 text-teal-800",
  FULFILLED: "bg-emerald-100 text-emerald-800",
  REJECTED: "bg-red-100 text-red-700",
  CANCELLED: "bg-slate-200 text-slate-700",
};

export function formatDateTime(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? ""
    : date.toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" });
}

/** Fetch bill PDF as a blob and trigger a real file download (avoids PWA/SPA navigation). */
export async function downloadBillPdf(url: string, orderNumber?: string | null) {
  const response = await fetch(url, { credentials: "omit", cache: "no-store" });
  if (!response.ok) {
    throw new Error("Could not download bill PDF");
  }
  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = objectUrl;
  anchor.download = `${(orderNumber || "invoice").replace(/[^\w.-]+/g, "_")}.pdf`;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(objectUrl);
}
