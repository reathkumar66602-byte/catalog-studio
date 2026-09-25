/**
 * Builds a Meesho-style leaf category catalog (name + full path) for Catalog Studio
 * category search — same shape Manage Order uses (id, name/label, path).
 */
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const outBackend = join(root, "catalog-studio-backend/src/main/resources/extension/meesho-categories.json");
const outExt = join(root, "catalog-studio-extension/src/content/shared/meeshoCategories.generated.json");

/** @type {Record<string, any>} */
const TREE = {
  "Women Fashion": {
    "Ethnic Wear": {
      "Kurtis, Sets & Fabrics": ["Kurtis", "Anarkali Kurtis", "Straight Kurtis", "A-Line Kurtis", "Long Kurtis", "Short Kurtis", "Feeding Kurtis", "Plus Size Kurtis", "Kurti Fabrics", "Kurta Sets", "Kurti Sets", "Sharara Sets", "Palazzo Sets", "Pant Sets", "Dupatta Sets", "Dress Materials", "Unstitched Dress Materials"],
      Sarees: ["Sarees", "Cotton Sarees", "Silk Sarees", "Georgette Sarees", "Chiffon Sarees", "Net Sarees", "Banarasi Sarees", "Bridal Sarees", "Party Wear Sarees", "Daily Wear Sarees"],
      Lehengas: ["Lehengas", "Bridal Lehengas", "Party Wear Lehengas", "Lehenga Cholis"],
      Blouses: ["Blouses", "Readymade Blouses", "Blouse Pieces"],
      Dupattas: ["Dupattas", "Stole", "Dupatta Sets"],
      "Ethnic Bottomwear": ["Palazzo", "Sharara", "Salwar", "Churidar", "Ethnic Skirts", "Patiala"],
      "Ethnic Outerwear": ["Ethnic Jackets", "Shrugs", "Nehru Jackets"],
    },
    "Western Wear": {
      "Tops, Tshirts & Shirts": ["Tops & Tunics", "Tops", "Tunics", "T-shirts", "Shirts", "Crop Tops", "Tank Tops", "Plus Size Tops"],
      "Dresses, Gowns & Jumpsuits": ["Dresses", "Frocks", "Western Gowns", "Gowns", "Jumpsuits", "Maxi Dresses", "Midi Dresses", "Plus Size Dresses"],
      Bottomwear: ["Jeans & Jeggings", "Jeans", "Jeggings", "Palazzos", "Trousers & Pants", "Leggings", "Shorts & Skirts", "Capris", "Plus Size Bottomwear"],
      Outerwear: ["Jackets", "Sweatshirts", "Sweaters", "Coats", "Blazers & Waistcoats", "Capes, Shrug & Ponchos", "Shawls & Stoles"],
      "Co-ords": ["Tops & Bottom Sets", "Co-ord Sets"],
    },
    Lingerie: {
      Innerwear: ["Women Bra", "Women Panties", "Shapewear", "Camisoles", "Other Innerwear", "Feeding Bras"],
      Sleepwear: ["Women Nightsuits", "Women Nightdress", "Nighties", "Other Sleepwear", "Bathrobes & Nightwear"],
      "Sports Wear": ["Sports Bra", "Sports Bottomwear", "Top & Bottom Sets"],
    },
  },
  Men: {
    "Top Wear": ["Shirts", "T-Shirts", "Summer T-Shirts", "T-Shirts Combos", "Shirts Combo", "Kurtas", "Hoodies", "Sweatshirts", "Jackets"],
    "Bottom Wear": ["Jeans", "Cargos/Trousers", "Trackpants", "Shorts", "Pyjamas", "Dhotis/Lungis"],
    Sets: ["Kurta Sets", "Night Suits"],
    Innerwear: ["Vests", "Briefs", "Trunks"],
    Ethnic: ["Kurtas", "Kurta Sets", "Nehru Jacket"],
    Footwear: ["Men Casual Shoes", "Men Sports Shoes", "Men Flip Flops and Sandals", "Men Formal Shoes", "Loafers"],
    Accessories: ["Watches", "Wallets", "Belts", "Sunglasses & Spectacle Frames", "Jewellery"],
  },
  "Kids & Toys": {
    "Kids - Boys Western Wear": {
      Infants: ["Bodysuit", "Romper", "Onesie", "Infant Sets", "Infant Tops", "Infant Bottoms", "Infant Nightwear"],
      "0-2 Years": ["T-shirts", "Shirts", "Shorts", "Trackpants", "Clothing Sets", "Nightwear"],
      "2-8 Years": ["T-shirts", "Shirts", "Jeans", "Shorts", "Trackpants", "Clothing Sets", "Jackets", "Sweaters"],
      "8-16 Years": ["T-shirts", "Shirts", "Jeans", "Trackpants", "Hoodies", "Jackets"],
    },
    "Kids - Girls Western Wear": {
      Infants: ["Bodysuit", "Romper", "Onesie", "Infant Dresses", "Infant Sets", "Infant Tops", "Infant Bottoms"],
      "0-2 Years": ["Frocks & Dresses", "Tops", "T-shirts", "Leggings", "Clothing Sets", "Nightwear"],
      "2-8 Years": ["Frocks & Dresses", "Tops", "T-shirts", "Jeans", "Skirts", "Leggings", "Clothing Sets", "Jackets"],
      "8-16 Years": ["Dresses", "Tops", "T-shirts", "Jeans", "Leggings", "Jackets", "Sweaters"],
    },
    "Kids - Boys Ethnic Wear": ["Kurtas", "Kurta Sets", "Dhoti Kurtas", "Nehru Jackets"],
    "Kids - Girls Ethnic Wear": ["Kurtis", "Lehengas", "Ghagra Cholis", "Salwar Sets", "Sharara Sets"],
    Babies: ["Newborn Care", "Baby Bedding & Accessories", "Diapers", "Baby Mosquito nets", "Baby Dry Sheets"],
    "Toys & Games": ["Soft Toys", "Educational Toys", "Outdoor Toys", "Board Games", "Action Figures", "Dolls", "Balls", "Remote Control Toys"],
    Accessories: ["Bags & Backpacks", "Kids Accessories", "Boys Shoes", "Girls Shoes", "Caps & Hats"],
  },
  "Home & Kitchen": {
    Decor: ["Artificial Plants", "Showpieces & Idols", "Clocks & Wall Decor", "Wallpapers & Stickers", "Lighting", "Wind Chimes & Hanging Decor", "Candle Holders & Tealight holder", "Key Holders", "Festive Decor", "Gifts & Home Decor"],
    Kitchen: ["Cookware", "Kitchen Tools", "Dinnerware", "Glasses & Barware", "Kitchen Linen", "Kitchen Appliances", "Storage & Organizers"],
    Bedding: ["Bedsheets", "Pillow, Cushion & Covers", "Blankets & Comforters", "Curtains & Accessories", "Doormats & Carpets"],
    Utility: ["Bathroom Accessories", "Cleaning Supplies", "Gardening", "Home Tools", "Insect Protection", "Sewing & Tailoring Accessories", "Shoe Racks", "Collapsible Wardrobes", "Wall Shelves", "Home Temple", "Pooja Needs", "Party Supplies", "Covers"],
  },
  "Beauty & Health": {
    Makeup: ["Lipstick", "Lip Gloss", "Eye Shadow and Liner", "Kajal", "Mascara", "Face Makeup", "Foundation", "Compact", "Blush", "Highlighter", "Makeup Kits & Combos", "Nail Makeup", "Brushes & Accessories"],
    Skincare: ["Face Wash", "Face Oil & Serum", "Face Masks & Peels", "Whitening Creams", "Body Lotion", "Soaps & Scrubs", "Sunscreen"],
    Haircare: ["Hair Oil & Shampoo", "Hair Curlers", "Straighteners & Dryers", "Hair Gels, Wax & Spray", "Hair Removal"],
    Fragrance: ["Perfumes & More", "Men Perfumes & Deodorant", "Attar", "Body Mist"],
    Healthcare: ["Healthcare", "Ayurveda & Nutrition", "Sanitary Pads & More", "Medical Devices", "Support Wear", "Oral Care", "Ear Cleaner", "Foot care", "Sexual Wellness", "Health Monitor & Massagers"],
    "Men Grooming": ["Trimmers", "Beard Oil", "Men's Face & Body Care", "Budget Grooming Kits"],
    "Mom & Baby": ["Baby Care Essentials", "Mom Care"],
  },
  Grocery: {
    "Dry Fruits & Nuts": ["Almonds", "Cashews", "Raisins", "Walnuts", "Pistachios", "Dry Fruits Mix", "Dates"],
    "Masala and spices": ["Turmeric", "Red Chilli", "Coriander Powder", "Garam Masala", "Cumin", "Mustard Seeds", "Ajwain", "Whole Spices", "Kitchen Masala Combos"],
    "Snacks and Namkeens": ["Namkeen", "Chips", "Bhujia", "Mixture", "Sev"],
    "Pickles & Chutneys": ["Pickles", "Amla Pickle", "Mango Pickle", "Lemon Pickle", "Chutneys", "Pickles & Chutneys"],
    "Biscuits and cookies": ["Biscuits", "Cookies", "Rusks"],
    "Chocolates & Candies": ["Chocolates", "Candies", "Toffees"],
    Beverages: ["Tea", "Coffee", "Drinks & Syrups", "Health Drinks"],
    Staples: ["Flour", "Rice", "Pulses", "Oils", "Sugar & Jaggery", "Salt"],
  },
  "Jewellery & Accessories": {
    Jewellery: ["Jewellery Sets", "Earrings", "Necklaces & Chains", "Bangles & Bracelets", "Mangalsutras", "Anklets & Nosepins", "Kamarbandh & Maangtika", "Rings", "All Jewellery"],
    Accessories: ["Hair Accessories", "Women Belts", "Scarves, Stoles & Gloves", "Women Watches", "Men Watches", "Wallets", "Sunglasses & Spectacle Frames", "Belts", "Men Jewellery"],
  },
  "Bags & Footwear": {
    Footwear: ["Heels and Sandals", "Flats", "Boots", "Flipflops & Slippers", "Bellies and Ballerinas", "Casual Shoes", "Sandals", "Sports Shoes"],
    Bags: ["Handbags", "Slingbags", "Backpacks", "Clutches", "Waist Bags", "Crossbody Bags & Sling Bags", "Duffel & Trolley Bags", "Laptop & Messenger Bags", "Wallets"],
  },
  Electronics: {
    Audio: ["Bluetooth Earbuds", "Neckband", "Wired Earphone", "Speakers", "Microphone"],
    Mobile: ["Cases & Covers", "Mobile Holders", "Mobile Chargers & Cables", "Power Banks", "Screen Expanders & Magnifiers", "Selfie Stick & Ringlight"],
    Accessories: ["Tripod & Monopod", "Extension Cord", "Computer Accessories"],
  },
  "Sports & Fitness": {
    Fitness: ["Yoga", "Skipping Ropes", "Exercise Bands", "Sweat Belts", "Tummy Trimmers", "Hand Grip Strengthener", "Fitness Accessories", "Fitness Gears"],
    Sports: ["Cricket", "Football", "Badminton", "Volleyball", "Skating", "Swimming", "Fishing", "Cycles & Accessories"],
  },
  "Car & Motorbike": {
    Bike: ["Bike LED Lights", "Bike Covers", "Bike Accessories", "Helmets", "Safety Gear & Clothing", "Scooty & Activa Accessories"],
    Car: ["Interior Accessories", "Car Care & Cleaning", "Car Covers", "Car Exterior Accessories", "Car Mobile & Holders", "Car Repair Assistance"],
  },
  "Office Supplies & Stationery": {
    Writing: ["Pens & Pencils", "Diaries & Notebooks", "Markers"],
    Organizers: ["Files & Desks Organizers", "Adhesives & Tapes"],
    Craft: ["Art & Craft Supplies"],
  },
  Books: {
    General: ["Children's Books", "Motivational Books", "Novels", "Religious Books", "Economics & Commerce"],
    Academic: ["School Textbooks & Guides", "University Books & Guides", "Reference Books", "UPSC & Central Exam Preparation", "Competitive Exams Preparation", "All Academic Books"],
  },
  "Pet Supplies": {
    Dogs: ["Collars & Leashes", "Clothes & Grooming", "Food & Treats", "Pet Toys", "Pet Bowls"],
    Aquarium: ["Aquarium Accessories"],
  },
  "Musical Instruments": {
    Instruments: ["Dholaks & Drum sets", "Piano & Keyboard", "String Instruments", "Wind Instruments", "Musical Accessories", "All Musical Instruments"],
  },
};

const VARIANTS = [
  "",
  "Cotton",
  "Rayon",
  "Polyester",
  "Silk",
  "Georgette",
  "Chiffon",
  "Net",
  "Lycra",
  "Denim",
  "Printed",
  "Embroidered",
  "Plain",
  "Solid",
  "Striped",
  "Floral",
  "Party Wear",
  "Daily Wear",
  "Festive",
  "Casual",
  "Formal",
  "Plus Size",
  "Combo",
  "Pack of 2",
  "Pack of 3",
  "Pack of 4",
  "Multicolor",
  "Black",
  "White",
  "Blue",
  "Pink",
  "Red",
  "Green",
  "Yellow",
  "Maroon",
];

const EXTRA_LEAVES = {
  "Women Fashion / Western Wear / Tops, Tshirts & Shirts": ["Peplum Tops", "Shirt Tops", "Cold Shoulder Tops", "Off Shoulder Tops", "Halter Tops", "Boat Neck Tops", "Round Neck Tops", "V Neck Tops"],
  "Women Fashion / Western Wear / Dresses, Gowns & Jumpsuits": ["Bodycon Dresses", "A-Line Dresses", "Shift Dresses", "Shirt Dresses", "Skater Dresses", "Wrap Dresses"],
  "Women Fashion / Ethnic Wear / Kurtis, Sets & Fabrics": ["Flared Kurtis", "Asymmetric Kurtis", "Kaftan Kurtis", "Indo Western Kurtis", "Jacket Style Kurtis", "Angrakha Kurtis"],
  "Kids & Toys / Kids - Boys Western Wear / Infants": ["Infant Rompers", "Infant Jumpsuits", "Infant Sleepsuits", "Infant Bibs", "Infant Caps"],
  "Kids & Toys / Kids - Girls Western Wear / Infants": ["Infant Rompers", "Infant Jumpsuits", "Infant Sleepsuits", "Infant Frocks", "Infant Caps"],
  "Grocery / Masala and spices": ["Hing", "Fennel Seeds", "Fenugreek", "Bay Leaves", "Cloves", "Cardamom", "Black Pepper", "Cinnamon"],
  "Beauty & Health / Makeup": ["Concealer", "Primer", "Setting Spray", "Lip Balm", "Lip Liner", "Eyebrow Pencil", "False Eyelashes"],
  "Home & Kitchen / Kitchen": ["Tawa", "Kadhai", "Pressure Cooker", "Idli Maker", "Chopper", "Peeler", "Spatula Set", "Lunch Box"],
  "Bags & Footwear / Footwear": ["Kolhapuri", "Wedges", "Block Heels", "Stilettos", "Mojaris", "Juttis", "Crocs Style"],
  "Electronics / Mobile": ["Tempered Glass", "Back Covers", "Flip Covers", "Car Chargers", "Type C Cables", "Lightning Cables"],
};

function slug(parts) {
  return parts
    .join("-")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 80);
}

function flatten(node, trail, out, seen) {
  if (Array.isArray(node)) {
    for (const leaf of node) {
      pushLeaf(leaf, trail, out, seen);
      if (/kurti|top|dress|shirt|t-shirt|saree|jeans|legging|bodysuit|romper|frock|shoe|bag|pickle|lipstick|set/i.test(leaf)) {
        for (const variant of VARIANTS) {
          if (!variant) continue;
          if (leaf.toLowerCase().includes(variant.toLowerCase())) continue;
          pushLeaf(`${variant} ${leaf}`, trail, out, seen);
        }
      }
    }
    return;
  }
  for (const [name, child] of Object.entries(node)) {
    flatten(child, [...trail, name], out, seen);
  }
}

function pushLeaf(name, trail, out, seen) {
  const path = [...trail, name].join(" / ");
  const id = slug([...trail, name]);
  if (seen.has(id)) return;
  seen.add(id);
  out.push({ id, name, label: name, path });
}

const out = [];
const seen = new Set();
flatten(TREE, [], out, seen);

for (const [pathKey, leaves] of Object.entries(EXTRA_LEAVES)) {
  const trail = pathKey.split(" / ");
  for (const leaf of leaves) {
    pushLeaf(leaf, trail, out, seen);
    for (const variant of VARIANTS) {
      if (!variant) continue;
      pushLeaf(`${variant} ${leaf}`, trail, out, seen);
    }
  }
}

// High-frequency apparel leaves first (⭐-like ordering for empty search).
const PRIORITY = /bodysuit|kurti|saree|dress|top|t-shirt|shirt|jeans|legging|frock|lehenga|blouse|romper|infant/i;
out.sort((a, b) => {
  const ap = PRIORITY.test(a.name) ? 0 : 1;
  const bp = PRIORITY.test(b.name) ? 0 : 1;
  return ap - bp || a.path.localeCompare(b.path);
});

mkdirSync(dirname(outBackend), { recursive: true });
mkdirSync(dirname(outExt), { recursive: true });
writeFileSync(outBackend, JSON.stringify(out));
writeFileSync(outExt, JSON.stringify(out));
console.log(`Wrote ${out.length} categories`);
console.log(outBackend);
console.log(outExt);
