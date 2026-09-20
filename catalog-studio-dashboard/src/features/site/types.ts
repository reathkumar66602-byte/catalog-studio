export type SiteBranding = {
  siteName: string;
  tagline?: string;
  heroTitle: string;
  heroSubtitle?: string;
  logoUrl?: string;
  primaryColor?: string;
  accentColor?: string;
  heroBackground?: string;
  footerText?: string;
};

export type SiteClient = {
  id: string;
  storeName: string;
  slug: string;
  ownerName?: string;
  email?: string;
  phone?: string;
  address?: string;
  websiteUrl?: string;
  logoUrl?: string;
  tagline?: string;
  about?: string;
  branding?: Record<string, unknown>;
  featured?: boolean;
  status?: string;
};

export type SitePromo = {
  id?: string;
  clientId?: string;
  storeName?: string;
  code: string;
  headline?: string;
  description?: string;
  discountType: string;
  discountValue: number;
  trialDays: number;
  validFrom?: string;
  validUntil?: string;
  status?: string;
};

export type SitePublic = {
  branding: SiteBranding;
  support: { email: string; phone?: string };
  enquiry: { enabled: boolean; intro?: string; successMessage?: string };
  client: SiteClient | null;
  promoCodes: SitePromo[];
};

export type AdminSiteBundle = {
  settings: SiteBranding & {
    supportEmail: string;
    mailFromEmail?: string;
    mailFromName?: string;
    supportPhone?: string;
    enquiryEnabled: boolean;
    enquiryIntro?: string;
    enquirySuccessMessage?: string;
  };
  clients: SiteClient[];
  promoCodes: SitePromo[];
  enquiries: {
    id: string;
    name: string;
    email: string;
    phone?: string;
    storeName?: string;
    subject?: string;
    message: string;
    status: string;
    createdAt: string;
  }[];
};

export const FALLBACK_SITE: SitePublic = {
  branding: {
    siteName: "Catalog Studio",
    tagline: "Seller tools for listings, labels, and growth",
    heroTitle: "Crop labels, estimate profit, and fill listings faster",
    heroSubtitle:
      "Catalog Studio crops Flipkart and Meesho shipping labels in your browser, estimates Meesho margins, and pairs a Chrome extension that fills GST and HSN. You always submit the listing yourself.",
    logoUrl: "/logo.svg",
    primaryColor: "#0f766e",
    accentColor: "#38bdf8",
    heroBackground: "#07111f",
    footerText:
      "Catalog Studio helps sellers crop shipping labels, estimate profit, and autofill marketplace listings. You always submit the listing yourself.",
  },
  support: { email: "support@catalogstudio.in", phone: "+91 98765 43210" },
  enquiry: {
    enabled: true,
    intro:
      "Ask Catalog Studio or Krishna Store about label crop, the profit calculator, or the Chrome extension.",
    successMessage: "Thanks. We received your enquiry and will reply from the support email on file.",
  },
  client: {
    id: "krishna-store",
    storeName: "Krishna Store",
    slug: "krishna-store",
    ownerName: "Krishna",
    email: "krishna.store@catalogstudio.local",
    phone: "+91 90000 11111",
    address: "India",
    logoUrl: "/logo.svg",
    tagline: "Marketplace-ready seller workspace",
    about:
      "Krishna Store uses Catalog Studio to crop Flipkart and Meesho shipping labels, check margins in the calculator, and autofill listings with the Chrome extension.",
    branding: { primaryColor: "#0f766e", accentColor: "#f59e0b" },
  },
  promoCodes: [
    {
      code: "KRISHNA10",
      headline: "Krishna Store seller offer",
      description: "10% off Catalog Studio Pro for Krishna Store sellers",
      discountType: "PERCENT",
      discountValue: 10,
      trialDays: 0,
      validUntil: "2027-12-31",
    },
  ],
};
