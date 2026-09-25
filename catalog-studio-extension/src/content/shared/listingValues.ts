import type { MappedListing } from "./marketplaces";

export function listingFieldValues(listing: MappedListing): Record<string, string> {
  const manufacturerBlock = [
    listing.manufacturerName,
    listing.manufacturerAddress,
    listing.manufacturerPincode ? `Pincode ${listing.manufacturerPincode}` : "",
  ]
    .filter(Boolean)
    .join("\n");
  const packerBlock = [
    listing.packerName || listing.manufacturerName,
    listing.packerAddress || listing.manufacturerAddress,
    listing.packerPincode || listing.manufacturerPincode ? `Pincode ${listing.packerPincode || listing.manufacturerPincode}` : "",
  ]
    .filter(Boolean)
    .join("\n");

  const pairs: Array<[string[], string | undefined]> = [
    [["gst", "gst (%)", "gst%", "gst ()", "gst %", "tax rate", "tax"], listing.gst],
    [["hsn", "hsn code", "hsn code *"], listing.hsn],
    [["mrp", "mrp (rs.)", "mrp (rs)", "maximum retail price"], listing.mrp],
    [["selling price", "meesho price", "meesho price gst inclusive", "listing price", "listingprice", "price"], listing.sellingPrice],
    [["net weight", "net weight (gms)", "net weight (g)", "net weight (grams)", "product weight", "weight"], listing.netWeight],
    [["style code", "style code / product id", "style code/ product id", "style code/product id", "product id", "product sku", "sku", "sku id"], listing.skuId || listing.styleCode],
    [["product name", "name", "title"], listing.title],
    [["size", "selected size"], listing.size || listing.selectedSizes?.[0]],
    [["color"], listing.color],
    [["combo of", "combo"], listing.comboOf],
    [["pack of", "pack of / combo", "multipack"], listing.packOf || listing.netQuantity],
    [["net quantity", "net quantity (n)"], listing.netQuantity],
    [["fabric", "material", "material name", "bottomwear fabric"], listing.material],
    [["fabric length"], listing.fabricLength],
    [["generic name"], listing.genericName],
    [["pattern"], listing.pattern],
    [["print or pattern type", "print type", "pattern type", "print pattern"], listing.printType],
    [["country of origin", "origin"], listing.countryOfOrigin || "India"],
    [["manufacturer name", "manufacturer"], listing.manufacturerName],
    [["manufacturer address"], listing.manufacturerAddress],
    [["manufacturer pincode"], listing.manufacturerPincode || listing.packerPincode],
    [["packer name", "packer"], listing.packerName || listing.manufacturerName],
    [["packer address"], listing.packerAddress || listing.manufacturerAddress],
    [["packer pincode"], listing.packerPincode || listing.manufacturerPincode],
    [["importer name"], listing.manufacturerName || listing.packerName || listing.manufacturerPincode || listing.packerPincode ? "Not Required" : ""],
    [["importer address"], listing.manufacturerName || listing.packerName || listing.manufacturerPincode || listing.packerPincode ? "Not Required" : ""],
    [["importer pincode"], listing.manufacturerName || listing.packerName || listing.manufacturerPincode || listing.packerPincode ? "Not Required" : ""],
    [["manufacturer/packer/importer details", "manufacturer / packer / importer details", "packer details", "manufacturer details"], manufacturerBlock || packerBlock],
    [["sleeve", "sleeve type", "sleeve length"], listing.sleeveLength || listing.sleeveType],
    [["neck", "neck type", "neckline"], listing.neckType],
    [["fit", "fit/shape", "fit / shape", "fit shape", "fit type", "fits"], listing.fit],
    [["occasion"], listing.occasion],
    [["type", "product type", "garment type"], listing.garmentType],
    [["wash care"], listing.washCare],
    [["sleeve styling"], listing.sleeveStyling],
    [["surface styling"], listing.surfaceStyling],
    [["ornamentation"], listing.ornamentation],
    [["stitch type", "stitch"], listing.stitchType],
    [["length", "kurti length", "dress length"], listing.garmentLength],
    [["main category"], listing.mainCategory],
    [["brand", "brand name"], listing.brand],
    [["sustainable"], "No"],
    [["waist rise"], listing.waistRise],
    [["closure"], listing.closure],
    [["description", "product description"], listing.description],
    [["weight (g)", "package weight", "dead weight"], listing.packageWeight],
    [["length (cm)", "package length"], listing.packageLength],
    [["width (cm)", "package width", "breadth (cm)"], listing.packageWidth],
    [["height (cm)", "package height"], listing.packageHeight],
    [["inventory", "qty", "quantity", "stock"], listing.inventory],
    [["length size"], listing.lengthSize || listing.fabricLength],
    [["breadth", "breadth (cm)"], listing.packageWidth],
    [["packaging type"], listing.packagingType],
    [["packaging unit"], listing.packagingUnit],
    [["packaging length"], listing.packageLength],
    [["packaging breadth"], listing.packageWidth],
    [["packaging height"], listing.packageHeight],
    [["packaging weight"], listing.packageWeight],
  ];

  const map: Record<string, string> = {};
  for (const [aliases, value] of pairs) {
    if (!value) continue;
    for (const alias of aliases) {
      map[normalizeKey(alias)] = value;
    }
  }
  return map;
}

export function normalizeKey(value: string) {
  return value
    .toLowerCase()
    .replace(/[%:/().,*]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function isMeasureLabel(label: string) {
  const needle = normalizeKey(label);
  if (!needle) return false;
  if (/^(shoulder|waist|hip|bust|chest) size/.test(needle)) return true;
  if (/^size length|length size/.test(needle)) return true;
  return /\binch|\bin\b/.test(needle) && /shoulder|waist|hip|bust|chest|length/.test(needle);
}

export function matchFieldValue(label: string, values: Record<string, string>) {
  const needle = normalizeKey(label).replace(/\*$/, "").trim();
  if (!needle || needle.length > 80) return "";
  if (isMeasureLabel(needle)) return "";
  if (values[needle]) return values[needle];
  const stripped = needle.replace(/\s*\([^)]*\)/g, "").trim();
  if (stripped && stripped !== needle && values[stripped]) return values[stripped];
  const keys = Object.keys(values).sort((a, b) => b.length - a.length);
  const prefix = keys.find((key) => {
    if (!(needle.startsWith(`${key} `) || key.startsWith(`${needle} `))) return false;
    const extra = (key.startsWith(`${needle} `) ? key.slice(needle.length) : needle.slice(key.length)).trim();
    if (/\b(cm|mm|inch|in|gms|g|grams|size|package|packaging)\b/.test(extra)) return false;
    return true;
  });
  if (prefix) return values[prefix];
  const contained = keys.find((key) => key.length >= 4 && needle.includes(key) && !/\b(cm|package|packaging|size)\b/.test(key));
  return contained ? values[contained] : "";
}

function slugPart(value: string) {
  return value.replace(/[^a-z0-9]+/gi, "-").replace(/^-|-$/g, "") || "Item";
}

export { slugPart };
