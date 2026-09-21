import type { SelectorHint } from "./fieldFinder";

export type MappedListing = {
  title?: string;
  gender?: string;
  color?: string;
  pattern?: string;
  printType?: string;
  material?: string;
  description?: string;
  hsn?: string;
  gst?: string;
  netWeight?: string;
  styleCode?: string;
  genericName?: string;
  comboOf?: string;
  netQuantity?: string;
  ornamentation?: string;
  sleeveType?: string;
  sleeveLength?: string;
  neckType?: string;
  fit?: string;
  occasion?: string;
  size?: string;
  fabricLength?: string;
  countryOfOrigin?: string;
  manufacturerName?: string;
  manufacturerAddress?: string;
  manufacturerPincode?: string;
  packerName?: string;
  packerAddress?: string;
  packerPincode?: string;
  skuId?: string;
  lengthSize?: string;
  mrp?: string;
  sellingPrice?: string;
  brand?: string;
  stitchType?: string;
  garmentLength?: string;
  mainCategory?: string;
  packOf?: string;
  waistRise?: string;
  closure?: string;
  packageWeight?: string;
  packageLength?: string;
  packageWidth?: string;
  packageHeight?: string;
  packagingType?: string;
  packagingUnit?: string;
  inventory?: string;
  washCare?: string;
  garmentType?: string;
  sleeveStyling?: string;
  surfaceStyling?: string;
  selectedSizes?: string[];
};

export type FillStep = {
  hint: SelectorHint;
  value: string;
  type: "text" | "textarea" | "select" | "checkbox";
  element?: HTMLElement;
};

export function meeshoSteps(listing: MappedListing): FillStep[] {
  return [
    { hint: { labelText: "Copy input details to all product" }, value: "true", type: "checkbox" },
    { hint: { labelText: "HSN Code" }, value: listing.hsn || "", type: "select" },
    { hint: { labelText: "GST" }, value: listing.gst || "", type: "select" },
    { hint: { labelText: "GST (%)" }, value: listing.gst || "", type: "select" },
    { hint: { labelText: "GST %" }, value: listing.gst || "", type: "select" },
    { hint: { labelText: "Tax Rate" }, value: listing.gst || "", type: "select" },
    { hint: { labelText: "MRP" }, value: listing.mrp || "", type: "text" },
    { hint: { labelText: "Selling Price" }, value: listing.sellingPrice || "", type: "text" },
    { hint: { labelText: "Net Weight" }, value: listing.netWeight || "", type: "text" },
    { hint: { labelText: "Net Weight (gms)" }, value: listing.netWeight || "", type: "text" },
    { hint: { labelText: "Style code" }, value: listing.styleCode || "", type: "text" },
    { hint: { labelText: "Style code/ Product ID" }, value: listing.styleCode || "", type: "text" },
    { hint: { labelText: "Style Code / Product ID" }, value: listing.styleCode || "", type: "text" },
    { hint: { labelText: "Product SKU" }, value: listing.skuId || listing.styleCode || "", type: "text" },
    { hint: { labelText: "Product Name" }, value: listing.title || "", type: "text" },
    { hint: { labelText: "Title" }, value: listing.title || "", type: "text" },
    { hint: { labelText: "Item name" }, value: listing.title || "", type: "text" },
    { hint: { labelText: "Product Weight" }, value: listing.netWeight || "", type: "text" },
    { hint: { labelText: "Size" }, value: listing.size || listing.selectedSizes?.[0] || "", type: "select" },
    { hint: { labelText: "Color" }, value: listing.color || "", type: "select" },
    { hint: { labelText: "Price" }, value: listing.sellingPrice || "", type: "text" },
    { hint: { labelText: "Meesho Price" }, value: listing.sellingPrice || "", type: "text" },
    { hint: { labelText: "Quantity" }, value: listing.inventory || "", type: "text" },
    { hint: { labelText: "Combo of" }, value: listing.comboOf || "", type: "select" },
    { hint: { labelText: "Combo" }, value: listing.comboOf || "", type: "select" },
    { hint: { labelText: "Pack of" }, value: listing.packOf || listing.netQuantity || "", type: "select" },
    { hint: { labelText: "SKU ID" }, value: listing.skuId || listing.styleCode || "", type: "text" },
    { hint: { labelText: "Length Size" }, value: listing.lengthSize || listing.fabricLength || "", type: "text" },
    { hint: { labelText: "Fabric" }, value: listing.material || "", type: "select" },
    { hint: { labelText: "Material" }, value: listing.material || "", type: "select" },
    { hint: { labelText: "Fabric Length" }, value: listing.fabricLength || "", type: "select" },
    { hint: { labelText: "Generic Name" }, value: listing.genericName || "", type: "select" },
    { hint: { labelText: "Net Quantity" }, value: listing.netQuantity || "", type: "select" },
    { hint: { labelText: "Pattern" }, value: listing.pattern || "", type: "select" },
    { hint: { labelText: "Print or Pattern Type" }, value: listing.printType || "", type: "select" },
    { hint: { labelText: "Stitch Type" }, value: listing.stitchType || "", type: "select" },
    { hint: { labelText: "Sleeve Length" }, value: listing.sleeveLength || listing.sleeveType || "", type: "select" },
    { hint: { labelText: "Length" }, value: listing.garmentLength || "", type: "select" },
    { hint: { labelText: "Main Category" }, value: listing.mainCategory || "", type: "select" },
    { hint: { labelText: "Brand" }, value: listing.brand || "", type: "select" },
    { hint: { labelText: "Fit/Shape" }, value: listing.fit || "", type: "select" },
    { hint: { labelText: "Waist Rise" }, value: listing.waistRise || "", type: "select" },
    { hint: { labelText: "Closure" }, value: listing.closure || "", type: "select" },
    { hint: { labelText: "Country of Origin" }, value: listing.countryOfOrigin || "India", type: "select" },
    { hint: { labelText: "Manufacturer Name" }, value: listing.manufacturerName || "", type: "text" },
    { hint: { labelText: "Manufacturer" }, value: listing.manufacturerName || "", type: "text" },
    { hint: { labelText: "Manufacturer Address" }, value: listing.manufacturerAddress || "", type: "text" },
    { hint: { labelText: "Manufacturer Pincode" }, value: listing.manufacturerPincode || listing.packerPincode || "", type: "text" },
    { hint: { labelText: "Same as Manufacturer Details" }, value: "true", type: "checkbox" },
    { hint: { labelText: "Packer Name" }, value: listing.packerName || listing.manufacturerName || "", type: "text" },
    { hint: { labelText: "Packer Address" }, value: listing.packerAddress || listing.manufacturerAddress || "", type: "text" },
    { hint: { labelText: "Packer Pincode" }, value: listing.packerPincode || listing.manufacturerPincode || "", type: "text" },
    { hint: { labelText: "Importer Name" }, value: "Not Required", type: "text" },
    { hint: { labelText: "Importer Address" }, value: "Not Required", type: "text" },
    { hint: { labelText: "Importer Pincode" }, value: "Not Required", type: "text" },
    { hint: { labelText: "Sleeve" }, value: listing.sleeveType || "", type: "select" },
    { hint: { labelText: "Neck" }, value: listing.neckType || "", type: "select" },
    { hint: { labelText: "Fit" }, value: listing.fit || "", type: "select" },
    { hint: { labelText: "Fit/ Shape" }, value: listing.fit || "", type: "select" },
    { hint: { labelText: "Occasion" }, value: listing.occasion || "", type: "select" },
    { hint: { labelText: "Type" }, value: listing.garmentType || "", type: "select" },
    { hint: { labelText: "Wash Care" }, value: listing.washCare || "", type: "select" },
    { hint: { labelText: "Sleeve Styling" }, value: listing.sleeveStyling || "", type: "select" },
    { hint: { labelText: "Surface Styling" }, value: listing.surfaceStyling || "", type: "select" },
    { hint: { labelText: "Multipack" }, value: listing.packOf || listing.netQuantity || "", type: "select" },
    { hint: { labelText: "Material name" }, value: listing.material || "", type: "select" },
    { hint: { labelText: "Pattern Type" }, value: listing.printType || "", type: "select" },
    { hint: { labelText: "Print Pattern" }, value: listing.printType || "", type: "select" },
    { hint: { labelText: "Bottomwear Fabric" }, value: listing.material || "", type: "select" },
    { hint: { labelText: "Listing price" }, value: listing.sellingPrice || "", type: "text" },
    { hint: { labelText: "Stock" }, value: listing.inventory || "", type: "text" },
    { hint: { labelText: "Breadth" }, value: listing.packageWidth || "", type: "text" },
    { hint: { labelText: "Breadth (cm)" }, value: listing.packageWidth || "", type: "text" },
    { hint: { labelText: "Ornamentation" }, value: listing.ornamentation || "", type: "select" },
    { hint: { labelText: "Manufacturer Details" }, value: listing.manufacturerName || "", type: "textarea" },
    { hint: { labelText: "Packer Details" }, value: listing.packerName || listing.manufacturerName || "", type: "textarea" },
    { hint: { labelText: "Weight" }, value: listing.netWeight || "", type: "text" },
    { hint: { labelText: "Weight (g)" }, value: listing.packageWeight || "", type: "text" },
    { hint: { labelText: "Length (cm)" }, value: listing.packageLength || "", type: "text" },
    { hint: { labelText: "Width (cm)" }, value: listing.packageWidth || "", type: "text" },
    { hint: { labelText: "Height (cm)" }, value: listing.packageHeight || "", type: "text" },
    { hint: { labelText: "Packaging Type", css: "[id*='packaging_type' i], [name*='packaging_type' i]" }, value: listing.packagingType || "", type: "select" },
    { hint: { labelText: "Packaging Unit", css: "[id*='packaging_unit' i], [name*='packaging_unit' i]" }, value: listing.packagingUnit || "", type: "select" },
    { hint: { labelText: "Packaging Length" }, value: listing.packageLength || "", type: "text" },
    { hint: { labelText: "Packaging Breadth" }, value: listing.packageWidth || "", type: "text" },
    { hint: { labelText: "Packaging Height" }, value: listing.packageHeight || "", type: "text" },
    { hint: { labelText: "Packaging Weight" }, value: listing.packageWeight || "", type: "text" },
    { hint: { labelText: "Description" }, value: listing.description || "", type: "textarea" },
  ].filter((step, index, all) => {
    if (step.type === "checkbox") return all.findIndex((item) => item.hint.labelText === step.hint.labelText) === index;
    return Boolean(step.value) && all.findIndex((item) => item.hint.labelText === step.hint.labelText) === index;
  });
}

export function detectMarketplace(url = location.href) {
  if (url.includes("meesho")) return "MEESHO";
  return "UNKNOWN";
}
