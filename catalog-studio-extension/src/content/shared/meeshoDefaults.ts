export type CategoryDefaults = {
  gst: string;
  hsn: string;
  netWeight: string;
  size: string;
  fabricLength: string;
  countryOfOrigin: string;
  comboOf: string;
  netQuantity: string;
  stitchType: string;
  sleeveLength: string;
  garmentLength: string;
  packOf: string;
  packageWeight: string;
  packageLength: string;
  packageWidth: string;
  packageHeight: string;
  packagingType: string;
  packagingUnit: string;
  inventory: string;
  sellingPrice: string;
  mrp: string;
  washCare: string;
  garmentType: string;
  sleeveStyling: string;
  surfaceStyling: string;
  fit: string;
  occasion: string;
  fabric: string;
};

export function defaultsForCategory(category: string, notes = "") {
  const blob = `${category} ${notes}`.toLowerCase();
  const base: CategoryDefaults = {
    gst: "5",
    hsn: "61091000",
    netWeight: "250",
    size: "",
    fabricLength: "",
    countryOfOrigin: "India",
    comboOf: "Single",
    netQuantity: "1",
    stitchType: "Stitched",
    sleeveLength: "",
    garmentLength: "",
    packOf: "1",
    packageWeight: "200",
    packageLength: "24",
    packageWidth: "20",
    packageHeight: "4",
    packagingType: "Loose Packaging",
    packagingUnit: "cm",
    inventory: "5",
    sellingPrice: "499",
    mrp: "999",
    washCare: "Machine Wash",
    garmentType: "Regular",
    sleeveStyling: "Regular Sleeves",
    surfaceStyling: "None",
    fit: "Regular",
    occasion: "Casual",
    fabric: "Cotton",
  };
  if (/kurti fabric|unstitched|semi stitched/.test(blob)) {
    return {
      ...base,
      hsn: "61061000",
      netWeight: "200",
      size: "Semi Stitched",
      fabricLength: "2.5 Meters",
      stitchType: "Semi Stitched",
    };
  }
  if (/\bsaree/.test(blob)) {
    return { ...base, hsn: "54075290", netWeight: "400", stitchType: "Unstitched" };
  }
  if (/kurta set|kurti set/.test(blob)) {
    return {
      ...base,
      hsn: "6104",
      netWeight: "350",
      sleeveLength: "Three-Quarter Sleeves",
      garmentLength: "Knee Length",
      stitchType: "Stitched",
      packageLength: "25",
      packageWidth: "20",
      packageHeight: "2",
    };
  }
  if (/\bkurti|\bkurta/.test(blob)) {
    return {
      ...base,
      hsn: "6104",
      netWeight: "350",
      sleeveLength: "Long Sleeves",
      garmentLength: "Knee Length",
      stitchType: "Stitched",
    };
  }
  if (/pant|trouser|jean/.test(blob)) {
    return { ...base, hsn: "62034200", stitchType: "Stitched", garmentLength: "Full Length" };
  }
  if (/\bt-?shirt|\btee\b/.test(blob)) {
    return {
      ...base,
      hsn: "6109",
      netWeight: "200",
      stitchType: "Stitched",
      sleeveLength: "Short Sleeves",
      garmentLength: "Regular",
      garmentType: "Regular",
      fit: "Regular",
    };
  }
  if (/\btunic|\btops?\b/.test(blob)) {
    return {
      ...base,
      hsn: "6109",
      stitchType: "Stitched",
      sleeveLength: "Three-Quarter Sleeves",
      garmentLength: "Regular",
      garmentType: "Regular",
      fit: "Regular Fit",
    };
  }
  return base;
}

export function mapSleeveLength(sleeveType: string | undefined) {
  const value = (sleeveType || "").toLowerCase();
  if (!value) return "";
  if (/\bsleeveless\b|without sleeve/.test(value)) return "Sleeveless";
  if (/three[-\s]?quarter|3\s*\/\s*4/.test(value)) return "Three-Quarter Sleeves";
  if (/\bcap(\s|-)?sleeves?\b/.test(value)) return "Cap Sleeves";
  if (/\bbell(\s|-)?sleeves?\b|\bflared(\s|-)?sleeves?\b/.test(value)) return "Three-Quarter Sleeves";
  if (/\b(full|long)(\s|-)?sleeves?\b/.test(value)) return "Long Sleeves";
  if (/\b(half|short|puff|flutter)(\s|-)?sleeves?\b/.test(value)) return "Short Sleeves";
  if (/\broll-?up\b/.test(value)) return "Roll-Up Sleeves";
  return "";
}

export function mapSleeveStyling(blob: string, fallback = "") {
  const value = blob.toLowerCase();
  if (/\bbell(\s|-)?sleeves?\b/.test(value)) return "Bell Sleeves";
  if (/\bpuff(\s|-)?sleeves?\b/.test(value)) return "Puff Sleeves";
  if (/\bflutter(\s|-)?sleeves?\b/.test(value)) return "Flutter Sleeves";
  if (/\bcap(\s|-)?sleeves?\b/.test(value)) return "Cap Sleeves";
  if (/\broll-?up\b/.test(value)) return "Rolled-up Sleeves";
  return fallback;
}

export function mapGarmentLength(blob: string, fallback = "") {
  const value = blob.toLowerCase();
  if (/\bcrop(ped)?\b/.test(value)) return "Crop";
  if (/\bmaxi\b|\bankle\b/.test(value)) return "Maxi";
  if (/\bmidi\b|\bcalf\b/.test(value)) return "Calf Length";
  if (/\bknee\b/.test(value)) return "Knee Length";
  if (/\bfull length\b/.test(value)) return "Full Length";
  if (/\blong(line|\s+(tunic|top|kurti|dress|gown))\b/.test(value)) return "Long";
  if (fallback) return fallback;
  if (/\b(tunic|top|tee|t-shirt|kurti|kurta|dress|gown|shirt)\b/.test(value)) return "Regular";
  return "";
}

export function deriveFabric(material: string | undefined, ...parts: Array<string | undefined>) {
  const given = (material || "").trim();
  if (given && !/^(unknown|n\/a|none|other)$/i.test(given)) {
    return mapFabricName(given) || given;
  }
  return mapFabricName(parts.filter(Boolean).join(" ")) || "Cotton";
}

function mapFabricName(blob: string) {
  const text = blob.toLowerCase();
  if (/georgette/.test(text)) return "Georgette";
  if (/crepe/.test(text)) return "Crepe";
  if (/rayon|viscose/.test(text)) return "Rayon";
  if (/polyester/.test(text)) return "Polyester";
  if (/\bsilk\b/.test(text)) return "Silk";
  if (/linen/.test(text)) return "Linen";
  if (/\bnet\b/.test(text)) return "Net";
  if (/cotton/.test(text)) return "Cotton";
  return "";
}

export function deriveGenericName(title: string, category: string, generatedName?: string, productType?: string) {
  const fromWords = genericFromBlob(`${title} ${category} ${generatedName} ${productType}`);
  if (fromWords) return fromWords;
  for (const candidate of [generatedName, productType, category]) {
    const cleaned = String(candidate || "").trim();
    if (cleaned && !isGarbageGeneric(cleaned)) return cleaned;
  }
  return "Top";
}

function genericFromBlob(blob: string) {
  const text = blob.toLowerCase();
  if (/\btunic/.test(text)) return "Tunic";
  if (/kurti fabric/.test(text)) return "Kurti Fabric";
  if (/\bkurti/.test(text)) return "Kurti";
  if (/\bkurta/.test(text)) return "Kurta";
  if (/\bt-?shirt|\btee\b/.test(text)) return "T-shirt";
  if (/\btops?\b/.test(text)) return "Top";
  if (/\bsaree/.test(text)) return "Saree";
  if (/\bdress/.test(text)) return "Dress";
  return "";
}

function isGarbageGeneric(value: string) {
  return value.length > 28 || /[/|]/.test(value) || /dobara|image lein|untitled|select category|women fashion|tops, tshirts/i.test(value);
}

export function deriveOccasion(occasion: string | undefined, ...parts: Array<string | undefined>) {
  const blob = `${occasion || ""} ${parts.filter(Boolean).join(" ")}`.toLowerCase();
  if (/festive|wedding|ethnic/.test(blob)) return "Festive";
  if (/party/.test(blob)) return "Party";
  if (/office|formal/.test(blob)) return "Office";
  if (/daily/.test(blob)) return "Daily";
  if ((occasion || "").trim()) return occasion!.trim();
  return "Casual";
}

export function deriveBrand(title: string, storeName: string) {
  if (!title) return storeName;
  const stop = /^(kurti|saree|dress|shirt|top|fabric|cotton|embroidered|printed|women|womens|men|mens|plus|size)$/i;
  const words = title.split(/\s+/);
  const kept: string[] = [];
  for (const word of words) {
    if (stop.test(word) && kept.length) break;
    if (!stop.test(word)) kept.push(word);
  }
  return kept.slice(0, 4).join(" ") || storeName;
}

export function deriveMainCategory(title: string, notes: string) {
  const blob = `${title} ${notes}`.toLowerCase();
  if (/plus size|3xl|4xl|5xl|6xl/.test(blob)) return "Plus Size";
  return "";
}

export function mapNeck(neckType: string | undefined) {
  const value = (neckType || "").toLowerCase();
  if (!value) return "";
  if (/round/.test(value)) return "Round Neck";
  if (/v[\s-]?neck/.test(value)) return "V-Neck";
  if (/boat/.test(value)) return "Boat Neck";
  if (/collar|shirt/.test(value)) return "Mandarin Collar";
  return neckType || "";
}

export function detectOrnamentation(...parts: Array<string | undefined>) {
  const blob = parts.filter(Boolean).join(" ").toLowerCase();
  if (/embroider/.test(blob)) return "Embroidered";
  if (/sequin/.test(blob)) return "Sequinned";
  if (/mirror/.test(blob)) return "Mirror Work";
  if (/lace/.test(blob)) return "Lace";
  return "Not Applicable";
}

export function buildStyleCode(color: string, productType: string, existing?: string) {
  if (existing?.trim()) return existing.trim();
  const colorPart = slugPart(color || "Item");
  const typePart = slugPart(productType || "Product");
  const suffix = String(Date.now()).slice(-4);
  return `${colorPart}-${typePart}-D${suffix}`.slice(0, 40);
}

export function extractPincode(address: string) {
  const match = address.match(/\b(\d{6})\b/);
  return match?.[1] || "";
}

function slugPart(value: string) {
  return value.replace(/[^a-z0-9]+/gi, "-").replace(/^-|-$/g, "") || "Item";
}
