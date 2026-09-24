import { describe, expect, it } from "vitest";
import { findField } from "../src/content/shared/fieldFinder";
import { fillByHints, resetAutofill, stopAutofill, verifyFieldValue } from "../src/content/shared/autofillEngine";

describe("field detection", () => {
  it("matches fields by label", () => {
    document.body.innerHTML = `
      <label for="name">Product Name</label>
      <input id="name" />
    `;
    const match = findField({ labelText: "Product Name" });
    expect(["LABEL_BASED", "NEARBY_LABEL"]).toContain(match?.strategy);
  });

  it("does not throw when checking visibility of detached or invalid nodes", async () => {
    const { visible } = await import("../src/content/shared/fieldFinder");
    expect(visible(null)).toBe(false);
    expect(visible(undefined)).toBe(false);
    expect(visible(document.createElement("input"))).toBe(true);
    const gone = document.createElement("div");
    gone.style.display = "none";
    document.body.appendChild(gone);
    expect(visible(gone)).toBe(false);
    gone.remove();
    expect(() => visible(gone)).not.toThrow();
  });

  it("matches nearby labels such as Net Weight (gms)", () => {
    document.body.innerHTML = `
      <div>
        <p>Net Weight (gms)</p>
        <input />
      </div>
    `;
    const match = findField({ labelText: "Net Weight" });
    expect(match?.strategy).toBe("NEARBY_LABEL");
  });

  it("does not treat Size Length as the product Length dropdown", () => {
    document.body.innerHTML = `
      <div>
        <p>Size Length (INCH)</p>
        <input id="length_size" readonly placeholder="Select" />
      </div>
      <div>
        <p>Length</p>
        <select id="garment-length"><option>Select</option><option>Regular</option></select>
      </div>
    `;
    const match = findField({ labelText: "Length" });
    expect(match?.element.id).toBe("garment-length");
  });
});

describe("meesho listing steps", () => {
  it("includes manufacturer, packer, country, and size for a kurti fabric listing", async () => {
    const { meeshoSteps } = await import("../src/content/shared/marketplaces");
    const steps = meeshoSteps({
      title: "Women Kurti Fabric Cotton Floral",
      gst: "5",
      hsn: "61061000",
      netWeight: "200",
      styleCode: "White-Kurti-D010",
      color: "White",
      comboOf: "Single",
      material: "Cotton",
      fabricLength: "2.5 Meters",
      genericName: "Kurti Fabrics",
      netQuantity: "1",
      pattern: "Printed",
      printType: "Floral",
      countryOfOrigin: "India",
      manufacturerName: "Krishnasrstore",
      manufacturerAddress: "New Barrack pur",
      manufacturerPincode: "700131",
      packerName: "Krishnasrstore",
      packerAddress: "New Barrack pur",
      packerPincode: "700131",
      size: "Semi Stitched",
      ornamentation: "Embroidered",
      stitchType: "Stitched",
      sleeveLength: "Long Sleeves",
      brand: "TRENDY PLUS SIZE",
      mrp: "2795",
      sellingPrice: "850",
      description: "Women kurti fabric cotton floral printed.",
    });
    const labels = steps.map((step) => step.hint.labelText);
    expect(labels).toContain("Manufacturer Name");
    expect(labels).toContain("Packer Name");
    expect(labels).toContain("Country of Origin");
    expect(labels).toContain("Stitch Type");
    expect(labels).toContain("Sleeve Length");
    expect(labels).toContain("Brand");
    expect(labels).toContain("MRP");
    expect(steps.find((s) => s.hint.labelText === "Importer Name")?.value).toBe("Not Required");
  });
});

describe("listing field aliases", () => {
  it("maps GST (%) and manufacturer details from listing values", async () => {
    const { listingFieldValues, matchFieldValue } = await import("../src/content/shared/listingValues");
    const values = listingFieldValues({
      gst: "5",
      manufacturerName: "Krishna store",
      manufacturerAddress: "Surat, Gujarat",
      manufacturerPincode: "395003",
      mrp: "2795",
      stitchType: "Stitched",
      sleeveLength: "Long Sleeves",
      brand: "TRENDY PLUS SIZE",
      garmentLength: "Knee Length",
      packageLength: "25",
      comboOf: "Single",
      packOf: "1",
      netWeight: "200",
      styleCode: "White-Kurti-D010",
      packagingType: "Loose Packaging",
      packagingUnit: "cm",
      washCare: "Machine Wash",
      garmentType: "Regular",
      fit: "Regular",
      inventory: "5",
      sellingPrice: "850",
      printType: "Floral",
      packageWidth: "15",
    });
    expect(matchFieldValue("GST (%)", values)).toBe("5");
    expect(matchFieldValue("GST %", values)).toBe("5");
    expect(matchFieldValue("Tax Rate", values)).toBe("5");
    expect(matchFieldValue("Net Weight (gms)", values)).toBe("200");
    expect(matchFieldValue("Style code/ Product ID", values)).toBe("White-Kurti-D010");
    expect(matchFieldValue("Packaging Type", values)).toBe("Loose Packaging");
    expect(matchFieldValue("Packaging Unit", values)).toBe("cm");
    expect(matchFieldValue("Packaging Breadth", values)).toBe("15");
    expect(matchFieldValue("Manufacturer", values)).toBe("Krishna store");
    expect(matchFieldValue("MRP (Rs.)", values)).toBe("2795");
    expect(matchFieldValue("Stitch Type", values)).toBe("Stitched");
    expect(matchFieldValue("Sleeve Length", values)).toBe("Long Sleeves");
    expect(matchFieldValue("Length", values)).toBe("Knee Length");
    expect(matchFieldValue("Size Length (INCH)", values)).toBe("");
    expect(matchFieldValue("Shoulder Size (INCH)", values)).toBe("");
    expect(matchFieldValue("Length (cm)", values)).toBe("25");
    expect(matchFieldValue("Combo", values)).toBe("Single");
    expect(matchFieldValue("Pack of", values)).toBe("1");
    expect(matchFieldValue("Product Weight", values)).toBe("200");
    expect(matchFieldValue("Wash Care", values)).toBe("Machine Wash");
    expect(matchFieldValue("Fit/ Shape", values)).toBe("Regular");
    expect(matchFieldValue("Fit/Shape", values)).toBe("Regular");
    expect(matchFieldValue("Quantity", values)).toBe("5");
    expect(matchFieldValue("Meesho Price (GST Inclusive)", values)).toBe("850");
    expect(matchFieldValue("Listing price", values)).toBe("850");
    expect(matchFieldValue("Stock", values)).toBe("5");
    expect(matchFieldValue("Print Pattern", values)).toBe("Floral");
    expect(matchFieldValue("Breadth (cm)", values)).toBe("15");
  });

  it("does not fill garment Length from package length", async () => {
    const { listingFieldValues, matchFieldValue } = await import("../src/content/shared/listingValues");
    const values = listingFieldValues({ packageLength: "28", packageWidth: "22" });
    expect(matchFieldValue("Length", values)).toBe("");
    expect(matchFieldValue("Length (cm)", values)).toBe("28");
  });
});

describe("meesho defaults", () => {
  it("uses semi-stitched defaults for kurti fabrics", async () => {
    const { defaultsForCategory, detectOrnamentation, extractPincode } = await import("../src/content/shared/meeshoDefaults");
    const defaults = defaultsForCategory("Kurti Fabrics", "cotton embroidered");
    expect(defaults.size).toBe("Semi Stitched");
    expect(defaults.hsn).toBe("61061000");
    expect(defaults.gst).toBe("5");
    expect(defaults.packagingType).toBe("Loose Packaging");
    expect(defaults.packagingUnit).toBe("cm");
    expect(detectOrnamentation("white embroidered kurti")).toBe("Embroidered");
    expect(extractPincode("New Barrack pur 700131")).toBe("700131");
  });

  it("uses stitched plus-size kurta set defaults", async () => {
    const { defaultsForCategory } = await import("../src/content/shared/meeshoDefaults");
    const defaults = defaultsForCategory("Plus Size Kurta Sets", "maroon printed");
    expect(defaults.stitchType).toBe("Stitched");
    expect(defaults.hsn).toBe("6104");
    expect(defaults.netWeight).toBe("350");
    expect(defaults.sleeveLength).toBe("Three-Quarter Sleeves");
  });

  it("uses t-shirt defaults including wash care and short sleeves", async () => {
    const { defaultsForCategory } = await import("../src/content/shared/meeshoDefaults");
    const defaults = defaultsForCategory("T-shirts", "white cotton");
    expect(defaults.sleeveLength).toBe("Short Sleeves");
    expect(defaults.washCare).toBe("Machine Wash");
    expect(defaults.garmentType).toBe("Regular");
    expect(defaults.garmentLength).toBe("Regular");
  });

  it("maps tunic generic name, fabric, and occasion for Meesho dropdowns", async () => {
    const { defaultsForCategory, deriveFabric, deriveGenericName, deriveOccasion } = await import("../src/content/shared/meeshoDefaults");
    const defaults = defaultsForCategory("Tops & Tunics Teal Floral Embroidered Tunic");
    expect(defaults.sleeveLength).toBe("Three-Quarter Sleeves");
    expect(defaults.garmentLength).toBe("Regular");
    expect(defaults.fit).toBe("Regular Fit");
    expect(defaults.fabric).toBe("Cotton");
    expect(deriveGenericName("Teal Floral Embroidered Tunic", "Image dobara lein", "Image dobara lein")).toBe("Tunic");
    expect(deriveGenericName("Women's Navy Checked Shirt", "Shirts", "Shirt")).toBe("Shirt");
    expect(deriveFabric("", "Teal Floral Embroidered Tunic")).toBe("Cotton");
    expect(deriveFabric("Rayon Blend")).toBe("Rayon");
    expect(deriveOccasion("", "casual daily wear")).toBe("Daily");
  });

  it("maps bell sleeves to Meesho Sleeve Length and keeps tunic Length", async () => {
    const { defaultsForCategory, mapGarmentLength, mapSleeveLength, mapSleeveStyling } = await import("../src/content/shared/meeshoDefaults");
    expect(mapSleeveLength("Bell Sleeves")).toBe("Three-Quarter Sleeves");
    expect(mapSleeveLength("Casual V-Neck Bell Sleeve Top")).toBe("Three-Quarter Sleeves");
    expect(mapSleeveStyling("Casual V-Neck Bell Sleeve Top", "Regular Sleeves")).toBe("Bell Sleeves");
    expect(mapGarmentLength("Teal Floral Embroidered Tunic", "")).toBe("Regular");
    const defaults = defaultsForCategory("Tops & Tunics Teal Floral Embroidered Tunic");
    expect(defaults.sleeveLength).toBe("Three-Quarter Sleeves");
    expect(defaults.garmentLength).toBe("Regular");
  });

  it("uses a small listing inventory for net quantity and keeps bulk stock as one piece", async () => {
    const { netQuantityForListing } = await import("../src/content/shared/meeshoDefaults");
    expect(netQuantityForListing("2", "1", "200")).toBe("2");
    expect(netQuantityForListing("200", "1", "200")).toBe("1");
    expect(netQuantityForListing("5", "1", "5")).toBe("1");
    expect(netQuantityForListing("9", "3", "200")).toBe("3");
  });

  it("turns the photo length into a Meesho fabric-length label", async () => {
    const { fabricLengthFromMeters, meterAmount } = await import("../src/content/shared/meeshoDefaults");
    expect(fabricLengthFromMeters("1.5")).toBe("1.5 Meter");
    expect(fabricLengthFromMeters("2.5")).toBe("2.5 Meters");
    expect(meterAmount("1.5 Meter")).toBe("1.5");
    expect(fabricLengthFromMeters("")).toBe("");
    expect(meterAmount("no length")).toBe("");
    expect(fabricLengthFromMeters("5.5")).toBe("5.5 Meter");
    expect(meterAmount("6")).toBe("6");
  });

  it("uses the Meesho category instead of a kurti word in the title", async () => {
    const { defaultsForCategory, deriveGenericName } = await import("../src/content/shared/meeshoDefaults");
    const shirts = defaultsForCategory("Shirts", "Women Kurti style cotton");
    expect(shirts.sleeveLength).toBe("Long Sleeves");
    expect(shirts.garmentLength).toBe("Regular");
    expect(shirts.hsn).toBe("62063000");
    expect(shirts.fabricLength).toBe("");

    const tees = defaultsForCategory("T-shirts", "plain shirt");
    expect(tees.sleeveLength).toBe("Short Sleeves");
    expect(tees.hsn).toBe("6109");

    const saree = defaultsForCategory("Sarees", "unstitched silk");
    expect(saree.stitchType).toBe("Unstitched");
    expect(saree.fabricLength).toBe("5.5 Meter");
    expect(saree.hsn).toBe("54075290");
    expect(saree.garmentLength).toBe("");

    const dress = defaultsForCategory("Dresses", "floral kurti look");
    expect(dress.hsn).toBe("6204");
    expect(dress.sleeveLength).toBe("");
    expect(dress.garmentLength).toBe("Regular");
    expect(dress.fabricLength).toBe("");

    const tops = defaultsForCategory("Tops & Tunics", "red kurti");
    expect(tops.sleeveLength).toBe("Three-Quarter Sleeves");
    expect(tops.garmentLength).toBe("Regular");
    expect(tops.fabricLength).toBe("");

    const kurti = defaultsForCategory("Kurtis", "cotton");
    expect(kurti.sleeveLength).toBe("Long Sleeves");
    expect(kurti.garmentLength).toBe("Knee Length");
    expect(kurti.hsn).toBe("6104");

    expect(deriveGenericName("Banarasi Silk", "Sarees", "")).toBe("Saree");
    expect(deriveGenericName("Red Palazzo", "Palazzo", "")).toBe("Palazzo");
    expect(deriveGenericName("Girls Top and Bottom", "Co-ord Sets", "")).toBe("Co-ord Set");
    expect(deriveGenericName("Women's Navy Checked Shirt", "Shirts", "Shirt")).toBe("Shirt");
  });

  it("does not apply clothing defaults to grocery, home, beauty, or appliances", async () => {
    const { defaultsForCategory, isNonApparelCatalog } = await import("../src/content/shared/meeshoDefaults");
    const { measuresForSize, fallbackSizesForListing } = await import("../src/content/shared/meeshoFormFill");
    for (const category of [
      "Grocery Packaged Food Biscuits",
      "Home Utility",
      "Home & Kitchen",
      "Beauty & Personal Care",
      "Health & Wellness",
      "Appliances",
      "Automotive",
      "Sweets, Chocolates & Snacks",
    ]) {
      const defaults = defaultsForCategory(category, "cotton kurti long sleeves");
      expect(defaults.hsn, category).toBe("");
      expect(defaults.gst, category).toBe("");
      expect(defaults.fabric, category).toBe("");
      expect(defaults.stitchType, category).toBe("");
      expect(defaults.sleeveLength, category).toBe("");
      expect(defaults.washCare, category).toBe("");
      expect(defaults.fabricLength, category).toBe("");
      expect(defaults.countryOfOrigin, category).toBe("India");
      expect(defaults.mrp, category).toBe("999");
    }
    expect(isNonApparelCatalog("Women Fashion Western Wear Kurtis")).toBe(false);
    expect(measuresForSize("M", { title: "Women Kurti", catalogPath: "Grocery Packaged Food", mainCategory: "Biscuits" })).toBeNull();
    expect(fallbackSizesForListing({ catalogPath: "Home Utility", title: "Storage Box" })).toEqual([]);
    expect(measuresForSize("M", { title: "Women Kurti", mainCategory: "Kurtis" })?.length).toBe("42");
  });
});

describe("shop helpers", () => {
  it("reads meesho uid from the query string", async () => {
    const { meeshoUid, sanitizeStoreName } = await import("../src/content/shared/meeshoStore");
    expect(sanitizeStoreName("Krishnasrstore")).toBe("Krishnasrstore");
    history.replaceState({}, "", "/panel?supplierId=shop-22");
    expect(meeshoUid()).toBe("shop-22");
    history.replaceState({}, "", "/panel/v3/new/cataloging/y2ogj/catalogs/single/add");
    expect(meeshoUid()).toBe("y2ogj");
    expect(sanitizeStoreName("Purple")).toBe("");
    expect(sanitizeStoreName("Purple-Kurti-D5560")).toBe("");
    expect(sanitizeStoreName("Krishnasrstore")).toBe("Krishnasrstore");
  });
});

describe("meesho page scan", () => {
  it("plans fill steps from whatever fields are on the page", async () => {
    const { planMeeshoFill, fillSizeChart, sizesForListing } = await import("../src/content/shared/meeshoFormFill");
    document.body.innerHTML = `
      <div><p>GST %</p><select><option>5%</option></select></div>
      <div><p>MRP</p><input /></div>
      <div><p>Stitch Type</p><select><option>Stitched</option></select></div>
      <div><p>Neck</p><select><option>Round Neck</option></select></div>
      <div><p>Brand</p><input /></div>
      <button>XL</button><button>3XL</button>
      <table>
        <tr><th>Size</th><th>SKU ID</th><th>Length</th><th>Bust (in)</th></tr>
        <tr><td>XL</td><td><input /></td><td><input /></td><td><input /></td></tr>
      </table>
    `;
    const steps = planMeeshoFill({
      gst: "5",
      mrp: "1299",
      stitchType: "Stitched",
      neckType: "Round Neck",
      brand: "TRENDY PLUS SIZE",
      skuId: "Krishna-913",
      title: "Women Plus Size Kurta Set",
    });
    const labels = steps.map((step) => step.hint.labelText);
    expect(labels).toContain("GST %");
    expect(labels).toContain("MRP");
    expect(labels).toContain("Stitch Type");
    expect(labels).toContain("Neck");
    expect(labels).toContain("Brand");
    expect(labels.some((label) => /sku/i.test(label || ""))).toBe(false);
    const { detectPageSizes, detectSelectedPageSizes } = await import("../src/content/shared/meeshoFormFill");
    expect(detectPageSizes()).toEqual(expect.arrayContaining(["XL", "3XL"]));
    expect(detectSelectedPageSizes()).toEqual(["XL"]);
    expect(sizesForListing({ title: "Women Plus Size Kurta Set" })).toEqual(["XL"]);
    const chart = await fillSizeChart({ skuId: "Krishna-913", inventory: "5" });
    expect(chart.some((item) => item.ok && /sku/i.test(item.field))).toBe(true);
    expect((document.querySelector("table input") as HTMLInputElement).value).toContain("Krishna-913-XL");
  });

  it("reads split labels such as Net Weight (gms) and fills them", async () => {
    const { planMeeshoFill } = await import("../src/content/shared/meeshoFormFill");
    document.body.innerHTML = `
      <div>
        <p><span>Net Weight</span><span>(gms)</span></p>
        <input />
      </div>
      <div>
        <p>Style code/ Product ID</p>
        <input />
      </div>
      <div>
        <p>Packaging Type</p>
        <select><option>Box</option><option>Loose Packaging</option></select>
      </div>
    `;
    const steps = planMeeshoFill({
      netWeight: "250",
      styleCode: "Navy-Top-D123",
      packagingType: "Loose Packaging",
    });
    const labels = steps.map((step) => step.hint.labelText || "");
    expect(labels.some((label) => /net weight/i.test(label))).toBe(true);
    expect(labels.some((label) => /style code/i.test(label))).toBe(true);
    expect(labels).toContain("Packaging Type");
    expect(steps.find((step) => /net weight/i.test(step.hint.labelText || ""))?.value).toBe("250");
  });

  it("plans Fabric, Fit/Shape, Generic Name, Length, Occasion, and Sleeve Length", async () => {
    const { planMeeshoFill } = await import("../src/content/shared/meeshoFormFill");
    document.body.innerHTML = `
      <div><p>Fabric</p><select><option>Select</option><option>Cotton</option></select></div>
      <div><p>Fit/ Shape</p><select><option>Select</option><option>Regular</option><option>Regular Fit</option></select></div>
      <div><p>Generic Name</p><select><option>Select</option><option>Tunic</option></select></div>
      <div><p>Length</p><select><option>Select</option><option>Regular</option></select></div>
      <div><p>Occasion</p><select><option>Select</option><option>Casual</option><option>Daily</option></select></div>
      <div><p>Sleeve Length</p><select><option>Select</option><option>Three-Quarter Sleeves</option></select></div>
    `;
    const steps = planMeeshoFill({
      material: "Cotton",
      fit: "Regular Fit",
      genericName: "Tunic",
      garmentLength: "Regular",
      occasion: "Casual",
      sleeveLength: "Three-Quarter Sleeves",
    });
    const byLabel = Object.fromEntries(steps.map((step) => [step.hint.labelText, step.value]));
    expect(byLabel.Fabric).toBe("Cotton");
    expect(byLabel["Fit/ Shape"]).toBe("Regular Fit");
    expect(byLabel["Generic Name"]).toBe("Tunic");
    expect(byLabel.Length).toBe("Regular");
    expect(byLabel.Occasion).toBe("Casual");
    expect(byLabel["Sleeve Length"]).toBe("Three-Quarter Sleeves");
  });

  it("fills HSN before GST so the form can auto-set tax", async () => {
    const { planMeeshoFill } = await import("../src/content/shared/meeshoFormFill");
    document.body.innerHTML = `
      <div><p>GST %</p><select><option>5%</option></select></div>
      <div><p>HSN Code</p><input /></div>
      <div><p>MRP</p><input /></div>
    `;
    const steps = planMeeshoFill({ gst: "5", hsn: "6104", mrp: "1299" });
    const labels = steps.map((step) => step.hint.labelText || "");
    expect(labels.indexOf("HSN Code")).toBeGreaterThan(-1);
    expect(labels.indexOf("GST %")).toBeGreaterThan(labels.indexOf("HSN Code"));
  });

  it("fills selected size measurements, MRP, and price in the variation table", async () => {
    const { fillSizeChart, measuresForSize, sizeAliases } = await import("../src/content/shared/meeshoFormFill");
    document.body.innerHTML = `
      <table>
        <tr>
          <th>Size</th>
          <th>Meesho Price (GST Inclusive)</th>
          <th>MRP</th>
          <th>Quantity</th>
          <th>SKU ID</th>
          <th>Bust (INCH)</th>
          <th>Waist (INCH)</th>
          <th>Length (INCH)</th>
        </tr>
        <tr>
          <td>S</td>
          <td><input /></td>
          <td><input /></td>
          <td><input /></td>
          <td><input /></td>
          <td><input /></td>
          <td><input /></td>
          <td><input /></td>
        </tr>
        <tr>
          <td>M</td>
          <td><input /></td>
          <td><input /></td>
          <td><input /></td>
          <td><input /></td>
          <td><input /></td>
          <td><input /></td>
          <td><input /></td>
        </tr>
      </table>
    `;
    expect(sizeAliases("M (38)")).toEqual(expect.arrayContaining(["M", "38", "M (38)"]));
    const measures = measuresForSize("M", { title: "Women White Rayon Printed Top" });
    expect(measures?.bust).toBe("36");
    expect(measuresForSize("M", { title: "Women's Checked Casual Shirt", gender: "Women" })?.length).toBe("25");
    expect(measuresForSize("M", { title: "Women Kurti" })?.length).toBe("42");
    expect(measuresForSize("M", { title: "Women Kurti", mainCategory: "Tops & Tunics", genericName: "Top" })?.length).toBe("25");
    expect(measuresForSize("M", { title: "Banarasi", mainCategory: "Sarees", genericName: "Saree" })).toBeNull();
    expect(measuresForSize("XXL", { title: "Casual Top", mainCategory: "Tops & Tunics", genericName: "Top" })?.length).toBe("28");
    const chart = await fillSizeChart({
      skuId: "NS-1",
      inventory: "5",
      mrp: "500",
      sellingPrice: "488",
      title: "Women White Rayon Printed Top",
      selectedSizes: ["M"],
    });
    expect(chart.some((item) => item.ok && /mrp/i.test(item.field))).toBe(true);
    const rows = document.querySelectorAll("tr");
    const mInputs = rows[2].querySelectorAll("input");
    expect(mInputs[0].value).toBe("488");
    expect(mInputs[1].value).toBe("500");
    expect(mInputs[2].value).toBe("5");
    expect(mInputs[3].value).toContain("NS-1-M");
    expect(mInputs[4].value).toBe("36");
  });

  it("fills Meesho price, MRP, inventory, and SKU on div size rows", async () => {
    const { fillSizeChart } = await import("../src/content/shared/meeshoFormFill");
    document.body.innerHTML = `
      <div>
        <p>Copy price details to all sizes</p>
        <div>
          <p>M</p>
          <input id="meesho_price" type="number" />
          <input id="only_wrong_return_price" type="number" />
          <input id="product_mrp" type="number" />
          <input id="inventory" type="number" />
          <input id="supplier_sku_id" />
        </div>
        <div>
          <p>L</p>
          <input id="meesho_price" type="number" />
          <input id="only_wrong_return_price" type="number" />
          <input id="product_mrp" type="number" />
          <input id="inventory" type="number" />
          <input id="supplier_sku_id" />
        </div>
      </div>
    `;
    const chart = await fillSizeChart({
      skuId: "Teal-Tunic-D123",
      inventory: "5",
      mrp: "999",
      sellingPrice: "499",
      title: "Teal Floral Embroidered Tunic",
      selectedSizes: ["M", "L"],
    });
    expect(chart.some((item) => item.ok && /meesho price/i.test(item.field))).toBe(true);
    expect(chart.some((item) => item.ok && /sku/i.test(item.field))).toBe(true);
    const rows = document.querySelectorAll("body > div > div");
    const first = rows[0].querySelectorAll("input");
    const second = rows[1].querySelectorAll("input");
    expect(first[0].value).toBe("499");
    expect(first[1].value).toBe("498");
    expect(first[2].value).toBe("999");
    expect(first[3].value).toBe("5");
    expect(first[4].value).toContain("Teal-Tunic-D123-M");
    expect(second[0].value).toBe("499");
    expect(second[4].value).toContain("Teal-Tunic-D123-L");
  });

  it("fills inventory, unique SKU, and meter length on every size row", async () => {
    const { fillSizeChart } = await import("../src/content/shared/meeshoFormFill");
    document.body.innerHTML = `
      <div id="variant-grid">
        <div>
          <span>Size</span>
          <span>Meesho Price</span>
          <span>Wrong/Defective Return Price</span>
          <span>MRP</span>
          <span>Inventory *</span>
          <span>SKU ID (optional)</span>
          <span>Length Size * (METER)</span>
        </div>
        <div>
          <span>28</span>
          <input />
          <input />
          <input />
          <input />
          <input />
          <select><option>Select</option><option>1 Meter</option><option>1.5 Meter</option><option>2 Meter</option></select>
        </div>
        <div>
          <span>30</span>
          <input />
          <input />
          <input />
          <input />
          <input />
          <select><option>Select</option><option>1 Meter</option><option>1.5 Meter</option><option>2 Meter</option></select>
        </div>
      </div>
    `;
    await fillSizeChart({
      skuId: "Red-Top-and-Bot",
      inventory: "2",
      mrp: "500",
      sellingPrice: "200",
      title: "Girls Red Top and Bottom",
      selectedSizes: ["28", "30"],
    });
    const rows = document.querySelectorAll("#variant-grid > div");
    const first = rows[1].querySelectorAll("input");
    const second = rows[2].querySelectorAll("input");
    expect(first[0].value).toBe("200");
    expect(first[1].value).toBe("199");
    expect(first[2].value).toBe("500");
    expect(first[3].value).toBe("2");
    expect(second[3].value).toBe("2");
    expect(first[4].value).toBe("Red-Top-and-Bot-28");
    expect(second[4].value).toBe("Red-Top-and-Bot-30");
    expect((rows[1].querySelector("select") as HTMLSelectElement).selectedOptions[0].textContent).toBe("1 Meter");
    expect((rows[2].querySelector("select") as HTMLSelectElement).selectedOptions[0].textContent).toBe("1 Meter");
  });

  it("fills stacked inventory, SKU, and meter columns when sizes are not table rows", async () => {
    const { fillSizeChart } = await import("../src/content/shared/meeshoFormFill");
    document.body.innerHTML = `
      <div id="cols">
        <div><span>Size</span><span>28</span><span>38</span></div>
        <div><span>Inventory *</span><input /><input /></div>
        <div><span>SKU ID (optional)</span><input /><input /></div>
        <div>
          <span>Length Size * (METER)</span>
          <select><option>Select</option><option>1 Meter</option><option>2 Meter</option></select>
          <select><option>Select</option><option>1 Meter</option><option>2 Meter</option></select>
        </div>
      </div>
    `;
    await fillSizeChart({
      skuId: "Red-Top-and-Bot",
      inventory: "2",
      title: "Girls Red Top and Bottom",
      selectedSizes: ["28", "38"],
    });
    const columns = document.querySelectorAll("#cols > div");
    const inventory = columns[1].querySelectorAll("input");
    const sku = columns[2].querySelectorAll("input");
    const lengths = columns[3].querySelectorAll("select");
    expect(inventory[0].value).toBe("2");
    expect(inventory[1].value).toBe("2");
    expect(sku[0].value).toBe("Red-Top-and-Bot-28");
    expect(sku[1].value).toContain("Red-Top-and-Bot-38");
    expect(lengths[0].selectedOptions[0].textContent).toBe("1 Meter");
    expect(lengths[1].selectedOptions[0].textContent).toBe("1 Meter");
  });

  it("fills a flat size grid where each cell is a sibling", async () => {
    const { fillSizeChart } = await import("../src/content/shared/meeshoFormFill");
    document.body.innerHTML = `
      <div id="flat">
        <div>Size</div>
        <div>Meesho Price</div>
        <div>MRP</div>
        <div>Inventory *</div>
        <div>SKU ID (optional)</div>
        <div>Length Size * (METER)</div>
        <div>28</div><div><input /></div><div><input /></div><div><input /></div><div><input /></div>
        <div><select><option>Select</option><option>1 Meter</option><option>2 Meter</option></select></div>
        <div>30</div><div><input /></div><div><input /></div><div><input /></div><div><input /></div>
        <div><select><option>Select</option><option>1 Meter</option><option>2 Meter</option></select></div>
      </div>
    `;
    await fillSizeChart({
      skuId: "Red-Top-and-Bot",
      inventory: "2",
      mrp: "500",
      sellingPrice: "200",
      title: "Girls Red Top and Bottom",
      selectedSizes: ["28", "30"],
    });
    const inputs = document.querySelectorAll("#flat input");
    const selects = document.querySelectorAll("#flat select");
    expect(inputs[2].value).toBe("2");
    expect(inputs[6].value).toBe("2");
    expect(inputs[3].value).toBe("Red-Top-and-Bot-28");
    expect(inputs[7].value).toBe("Red-Top-and-Bot-30");
    expect(selects[0].selectedOptions[0].textContent).toBe("1 Meter");
    expect(selects[1].selectedOptions[0].textContent).toBe("1 Meter");
  });

  it("selects inch measurements in size-row dropdowns instead of leaving Select", async () => {
    const { fillSizeChart } = await import("../src/content/shared/meeshoFormFill");
    document.body.innerHTML = `
      <table>
        <tr>
          <th>Size</th>
          <th>Shoulder Size (INCH)</th>
          <th>Waist Size (INCH)</th>
          <th>Size Length (INCH)</th>
          <th>Hip Size (INCH)</th>
        </tr>
        <tr>
          <td>M</td>
          <td><select id="shoulder_size"><option>Select</option><option>14</option><option>15</option></select></td>
          <td><select id="waist_size"><option>Select</option><option>30</option><option>32</option></select></td>
          <td><select id="length_size"><option>Select</option><option>25</option><option>26</option></select></td>
          <td><select id="hip_size"><option>Select</option><option>38</option><option>40</option></select></td>
        </tr>
      </table>
    `;
    const chart = await fillSizeChart({
      title: "Teal Floral Embroidered Tunic",
      genericName: "Tops & Tunics",
      selectedSizes: ["M"],
    });
    expect(chart.some((item) => item.ok && item.field === "shoulder")).toBe(true);
    expect((document.getElementById("shoulder_size") as HTMLSelectElement).value).toBe("14");
    expect((document.getElementById("waist_size") as HTMLSelectElement).value).toBe("30");
    expect((document.getElementById("length_size") as HTMLSelectElement).value).toBe("25");
    expect((document.getElementById("hip_size") as HTMLSelectElement).value).toBe("38");
  });

  it("does not write Regular into a number input", async () => {
    document.body.innerHTML = `
      <label for="qty">Quantity</label>
      <input id="qty" type="number" />
    `;
    resetAutofill();
    const results = await fillByHints([{ hint: { labelText: "Quantity" }, value: "Regular" }]);
    expect((document.querySelector("input") as HTMLInputElement).value).toBe("");
    expect(results[0].ok).toBe(false);
  });

  it("reads sizes selected on the Meesho form including chips and dropdowns", async () => {
    const { detectSelectedPageSizes, detectPageSizes } = await import("../src/content/shared/meeshoFormFill");
    document.body.innerHTML = `
      <button>S</button>
      <button class="selected" aria-pressed="true">M</button>
      <button>L</button>
      <button>XL</button>
      <div role="combobox">M, L</div>
      <table>
        <tr><th>Size</th><th>Length</th></tr>
        <tr><td>M</td><td><input /></td></tr>
        <tr><td>L</td><td><input /></td></tr>
      </table>
    `;
    expect(detectPageSizes()).toEqual(expect.arrayContaining(["S", "M", "L", "XL"]));
    expect(detectSelectedPageSizes()).toEqual(expect.arrayContaining(["M", "L"]));
  });
});

describe("meesho category detection", () => {
  it("reads the highlighted picker path instead of leftover Kurtis text", async () => {
    const { readMeeshoCategoryFromPage, readMeeshoCategoryPath, isMeeshoProductDetailsPage } = await import("../src/content/shared/meeshoCatalog");
    document.body.innerHTML = `
      <h1>Add Single Catalog</h1>
      <div>1 Select Category</div>
      <div>2 Add Product Details</div>
      <div>
        <div class="selected">Women Fashion</div>
        <div>Men Fashion</div>
        <div>Kids</div>
      </div>
      <div>
        <div>Ethnic Wear</div>
        <div class="selected">Western Wear</div>
        <div>Lingerie</div>
      </div>
      <div>
        <div>Dresses, Gowns & Jumpsuits</div>
        <div class="selected">Tops, Tshirts & Shirts</div>
        <div>Kurtis, Sets & Fabrics</div>
      </div>
      <div>
        <div>T-shirts</div>
        <div class="selected">Tops & Tunics</div>
        <div>Kurtis</div>
      </div>
    `;
    expect(readMeeshoCategoryFromPage()).toBe("Tops & Tunics");
    expect(readMeeshoCategoryPath()).toEqual([
      "Women Fashion",
      "Western Wear",
      "Tops, Tshirts & Shirts",
      "Tops & Tunics",
    ]);
    expect(isMeeshoProductDetailsPage()).toBe(false);
  });

  it("maps a women's shirt to women shirts, not men's t-shirts", async () => {
    const { categoryClickPath } = await import("../src/content/shared/meeshoCatalog");
    document.body.innerHTML = "<main><h1>Add Single Catalog</h1></main>";
    await new Promise((resolve) => setTimeout(resolve, 550));
    expect(categoryClickPath({ gender: "Women", productType: "Shirt", name: "Women's Checked Casual Shirt" })).toEqual([
      "Women Fashion",
      "Western Wear",
      "Tops, Tshirts & Shirts",
      "Shirts",
    ]);
    expect(categoryClickPath({ gender: "Women", productType: "Top", name: "Women Cotton Fabric Printed Top" })).toEqual([
      "Women Fashion",
      "Western Wear",
      "Tops, Tshirts & Shirts",
      "Tops & Tunics",
    ]);
    expect(categoryClickPath({ gender: "Women", productType: "Saree", name: "Unstitched Banarasi Saree" })).toEqual([
      "Women Fashion",
      "Ethnic Wear",
      "Sarees",
    ]);
  });

  it("does not treat Catalog Uploads list or the extension sidebar as a product form", async () => {
    const {
      isMeeshoCatalogListPage,
      isMeeshoAddCatalogFlow,
      isMeeshoProductDetailsPage,
      shouldScanMeeshoForm,
      hostPageText,
    } = await import("../src/content/shared/meeshoCatalog");
    const listUrl = "https://supplier.meesho.com/panel/v3/new/cataloging/y2ogj/catalogs";
    document.body.innerHTML = `
      <aside id="cs-sidebar">
        <p>GST, HSN, weight, and manufacturer details</p>
        <input placeholder="Manufacturer / packer address" />
        <p>Product name</p>
      </aside>
      <main>
        <h1>Catalog Uploads</h1>
        <p>Total Uploads Done: 41</p>
      </main>
    `;
    expect(isMeeshoCatalogListPage(listUrl)).toBe(true);
    expect(isMeeshoAddCatalogFlow(listUrl)).toBe(false);
    expect(shouldScanMeeshoForm(listUrl)).toBe(false);
    expect(hostPageText()).not.toMatch(/manufacturer details/i);
    expect(isMeeshoProductDetailsPage()).toBe(false);
  });

  it("does not treat the Meesho supplier login screen as add catalog", async () => {
    const { isMeeshoAddCatalogFlow, isMeeshoSupplierLoginPage } = await import("../src/content/shared/meeshoCatalog");
    const loginUrl = "https://supplier.meesho.com/panel/v3/new/login";
    document.title = "meesho";
    document.body.innerHTML = `
      <h1>Login to your supplier panel</h1>
      <input placeholder="Email Id or mobile number" />
      <button>Create your supplier account</button>
    `;
    expect(isMeeshoSupplierLoginPage(loginUrl)).toBe(true);
    expect(isMeeshoAddCatalogFlow(loginUrl)).toBe(false);
    expect(isMeeshoAddCatalogFlow("https://supplier.meesho.com/panel/v3/new/cataloging/y2ogj/catalogs/add")).toBe(false);
  });

  it("recognizes Meesho bulk template upload as Meesho UI, not Catalog Studio UI", async () => {
    const { isMeeshoBulkTemplateStep } = await import("../src/content/shared/meeshoCatalog");
    document.body.innerHTML = `
      <h1>Bulk Catalog Upload</h1>
      <p>Already have your Tops &amp; Tunics template filled?</p>
      <button>+ Upload Template File</button>
      <button>Generate Prefilled Template</button>
      <a>Download Empty Template</a>
    `;
    expect(isMeeshoBulkTemplateStep()).toBe(true);
  });
});

describe("meesho store detection", () => {
  it("reads the manufacturer name already on the form", async () => {
    const { detectMeeshoStore, isMeeshoPageChrome, sanitizeStoreName } = await import("../src/content/shared/meeshoStore");
    document.body.innerHTML = `
      <div>
        <p>Manufacturer Name</p>
        <input value="Krishnasrstore" />
      </div>
    `;
    expect(sanitizeStoreName("Home")).toBe("");
    expect(sanitizeStoreName("Supplier Panel")).toBe("");
    expect(isMeeshoPageChrome("Login to Meesho Supplier Panel")).toBe(true);
    expect(isMeeshoPageChrome("Krishna store")).toBe(false);
    expect(sanitizeStoreName("Login to Meesho Supplier Panel")).toBe("");
    expect(sanitizeStoreName("Login to your supplier panel")).toBe("");
    expect(sanitizeStoreName("Krishna store ▼")).toBe("Krishna store");
    expect(detectMeeshoStore()?.name).toBe("Krishnasrstore");
  });

  it("reads Krishna store from the Manufacturer field without Name suffix", async () => {
    const { detectMeeshoStore } = await import("../src/content/shared/meeshoStore");
    document.body.innerHTML = `
      <div>
        <p>Manufacturer</p>
        <input value="Krishna store" />
      </div>
    `;
    expect(detectMeeshoStore()?.name).toBe("Krishna store");
  });

  it("reads the store name from page JSON", async () => {
    const { detectMeeshoStore } = await import("../src/content/shared/meeshoStore");
    document.body.innerHTML = `<script type="application/json">{"supplierName":"Krishna store"}</script>`;
    expect(detectMeeshoStore()?.name).toBe("Krishna store");
  });

  it("prefers Krishna store over a Meesho supplier id like y2ogj", async () => {
    const { detectMeeshoStore, sanitizeStoreName } = await import("../src/content/shared/meeshoStore");
    document.body.innerHTML = `
      <header>
        <span>y2ogj</span>
        <div class="profile"><span>Krishna store</span></div>
      </header>
      <script type="application/json">{"identifierName":"y2ogj","supplierId":"y2ogj","shopName":"Krishna store"}</script>
    `;
    document.cookie = "supplier_id=y2ogj; shopName=Krishna%20store";
    expect(sanitizeStoreName("y2ogj")).toBe("");
    expect(sanitizeStoreName("Krishna store")).toBe("Krishna store");
    expect(detectMeeshoStore()?.name).toBe("Krishna store");
  });

  it("does not treat the Meesho login title as the shop name", async () => {
    const { detectMeeshoStore, sanitizeStoreName } = await import("../src/content/shared/meeshoStore");
    document.title = "Login to Meesho Supplier Panel";
    document.body.innerHTML = `
      <header>
        <h1>Login to Meesho Supplier Panel</h1>
        <button>Login to Meesho Supplier Panel</button>
      </header>
      <div>
        <p>Manufacturer Name</p>
        <input value="Krishna store" />
      </div>
    `;
    expect(sanitizeStoreName("Login to Meesho Supplier Panel")).toBe("");
    expect(detectMeeshoStore()?.name).toBe("Krishna store");
  });
});

describe("checkbox fill", () => {
  it("checks a labeled marketplace checkbox", async () => {
    document.body.innerHTML = `
      <label><input type="checkbox" /> Copy input details to all product</label>
    `;
    resetAutofill();
    const results = await fillByHints([
      { hint: { labelText: "Copy input details to all product" }, value: "true", type: "checkbox" },
    ]);
    expect(results[0].ok).toBe(true);
    expect((document.querySelector("input") as HTMLInputElement).checked).toBe(true);
  });
});

describe("autofill engine", () => {
  it("fills a text input and verifies the value", async () => {
    document.body.innerHTML = `
      <label for="name">Product Name</label>
      <input id="name" />
    `;
    resetAutofill();
    const results = await fillByHints([{ hint: { labelText: "Product Name" }, value: "Navy Shirt" }]);
    expect(results[0].ok).toBe(true);
    expect(verifyFieldValue(document.querySelector("input")!, "Navy Shirt")).toBe(true);
  });

  it("selects GST 5% when the listing value is 5", async () => {
    document.body.innerHTML = `
      <label for="gst">GST %</label>
      <select id="gst">
        <option>3%</option>
        <option>5%</option>
        <option>12%</option>
      </select>
    `;
    resetAutofill();
    const results = await fillByHints([{ hint: { labelText: "GST %" }, value: "5", type: "select" }]);
    expect(results[0].ok).toBe(true);
    expect((document.querySelector("select") as HTMLSelectElement).selectedOptions[0].textContent).toBe("5%");
  });

  it("selects net quantity 2 and does not take 12", async () => {
    document.body.innerHTML = `
      <label for="nq">Net Quantity (N)</label>
      <select id="nq">
        <option>Select</option>
        <option>1</option>
        <option>2</option>
        <option>12</option>
        <option>Pack of 2</option>
      </select>
    `;
    resetAutofill();
    const results = await fillByHints([{ hint: { labelText: "Net Quantity (N)" }, value: "2", type: "select" }]);
    expect(results[0].ok).toBe(true);
    expect((document.getElementById("nq") as HTMLSelectElement).value).toBe("2");
  });

  it("stops when the user cancels", async () => {
    document.body.innerHTML = `<label for="name">Product Name</label><input id="name" />`;
    stopAutofill();
    const results = await fillByHints([{ hint: { labelText: "Product Name" }, value: "X" }]);
    expect(results[0].message).toContain("Stopped");
  });
});

describe("generate button", () => {
  it("enables generate when the listing is not busy", async () => {
    const { syncGenerateEnabled } = await import("../src/content/shared/generateButton");
    document.body.innerHTML = `<button id="cs-generate" disabled class="busy">Generate</button>`;
    syncGenerateEnabled();
    const btn = document.getElementById("cs-generate") as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
    expect(btn.classList.contains("busy")).toBe(false);
  });
});

describe("chrome access", () => {
  it("treats a reloaded extension as a safe no-op", async () => {
    const { ignoreInvalidated, isInvalidatedContext } = await import("../src/services/chromeAccess");
    expect(isInvalidatedContext(new Error("Extension context invalidated."))).toBe(true);
    const value = await ignoreInvalidated(async () => {
      throw new Error("Extension context invalidated.");
    }, "safe");
    expect(value).toBe("safe");
  });
});
