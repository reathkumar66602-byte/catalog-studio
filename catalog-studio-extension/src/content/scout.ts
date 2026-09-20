import { isExtensionAlive, sendRuntimeMessage, storageGet, storageSet } from "../services/chromeAccess";
import { getSession } from "../services/storage";

type Card = {
  title: string;
  price: number;
  ratings: number;
  rating: number;
  href: string;
};

const RATIO = 4;

function fmt(n: number) {
  return Math.round(n).toLocaleString("en-IN");
}

function money(n: number) {
  if (n >= 1e7) return `₹${(n / 1e7).toFixed(2)}Cr`;
  if (n >= 1e5) return `₹${(n / 1e5).toFixed(2)}L`;
  return `₹${fmt(n)}`;
}

function num(text: string) {
  const n = Number((text || "").replace(/[^\d.]/g, ""));
  return Number.isFinite(n) ? n : 0;
}

function scanCards(): Card[] {
  const nodes = Array.from(document.querySelectorAll<HTMLElement>('a[href*="/p/"], [class*="ProductCard"], [class*="productCard"]'));
  const seen = new Set<string>();
  const cards: Card[] = [];
  for (const node of nodes) {
    const link = (node.closest("a") || node.querySelector("a") || node) as HTMLAnchorElement;
    const href = link.href || "";
    if (!/\/p\//.test(href) || seen.has(href)) continue;
    const text = (node.innerText || "").replace(/\s+/g, " ");
    const price = num((text.match(/₹\s?([\d,]+)/) || [])[1] || "");
    const ratings = num((text.match(/([\d,]+)\s*(ratings?|reviews?)/i) || [])[1] || "");
    const rating = num((text.match(/(\d(?:\.\d)?)\s*\(?/) || [])[1] || "");
    const title = text.split("₹")[0].trim().slice(0, 90);
    if (!title || title.length < 4) continue;
    seen.add(href);
    cards.push({ title, price, ratings, rating, href });
  }
  return cards.slice(0, 60);
}

function inject() {
  if (document.getElementById("cs-scout") || !isExtensionAlive()) return;
  const fab = document.createElement("button");
  fab.id = "cs-scout-fab";
  fab.textContent = "SCOUT";
  const panel = document.createElement("aside");
  panel.id = "cs-scout";
  panel.innerHTML = `
    <header><strong>Catalog Studio Scout</strong><button id="cs-scout-x" style="background:transparent;color:#fff;border:0;cursor:pointer">×</button></header>
    <div class="body">
      <p class="note">Read-only. Scans product cards already on this page. Estimates pieces as ratings × 4.</p>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:6px">
        <label class="note">Ship ₹<input id="cs-scout-ship" type="number" value="62" /></label>
        <label class="note">Fees ₹<input id="cs-scout-fees" type="number" value="80" /></label>
      </div>
      <button class="btn" id="cs-scout-scan" style="margin-top:10px">Scan this page</button>
      <p id="cs-scout-sum" class="note"></p>
      <div id="cs-scout-list"></div>
    </div>
  `;
  document.documentElement.append(fab, panel);
  fab.onclick = () => panel.classList.toggle("open");
  panel.querySelector("#cs-scout-x")?.addEventListener("click", () => panel.classList.remove("open"));
  panel.querySelector("#cs-scout-scan")?.addEventListener("click", () => void runScan());
  chrome.runtime.onMessage.addListener((msg) => {
    if (msg?.type === "CS_TOGGLE_SCOUT") panel.classList.toggle("open");
  });
}

async function runScan() {
  const list = document.getElementById("cs-scout-list");
  const sum = document.getElementById("cs-scout-sum");
  if (!list || !sum) return;
  const cards = scanCards();
  const ship = Number((document.getElementById("cs-scout-ship") as HTMLInputElement)?.value || 62);
  const fees = Number((document.getElementById("cs-scout-fees") as HTMLInputElement)?.value || 80);
  await storageSet({ csScoutShip: ship, csScoutFees: fees });
  if (!cards.length) {
    sum.textContent = "No product cards found. Open a Meesho search or category page, then scan.";
    list.innerHTML = "";
    return;
  }
  const session = await getSession();
  let insights: Array<{ index: number; badge: string; reason: string }> = [];
  if (session) {
    const data = await sendRuntimeMessage<{ data?: { insights?: typeof insights; summary?: string } }>({
      type: "API",
      path: "/extension/competitor-analyze",
      init: { method: "POST", body: JSON.stringify({ pageType: location.pathname, items: cards }) },
    });
    insights = data?.data?.insights || [];
    if (data?.data?.summary) sum.textContent = data.data.summary;
  } else {
    sum.textContent = "Pair Catalog Studio to save AI notes. Local scan still works.";
  }
  const totalPieces = cards.reduce((n, c) => n + c.ratings * RATIO, 0);
  const totalSale = cards.reduce((n, c) => n + c.ratings * RATIO * c.price, 0);
  sum.textContent = `${cards.length} products · est. ${fmt(totalPieces)} pieces · ${money(totalSale)} sale`;
  list.innerHTML = cards
    .map((card, index) => {
      const insight = insights.find((item) => item.index === index);
      const pieces = card.ratings * RATIO;
      const sale = pieces * card.price;
      const profit = card.price - ship - fees;
      return `<article class="card"><strong>${escapeHtml(card.title)}</strong>
        <div>₹${fmt(card.price)} · ${fmt(card.ratings)} ratings · est. ${fmt(pieces)} pcs · ${money(sale)}</div>
        <div class="note">${insight ? `${insight.badge}: ${insight.reason}` : `Est. margin after ship+fees: ₹${fmt(profit)}`}</div></article>`;
    })
    .join("");
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[ch] || ch);
}

if (document.body) inject();
else document.addEventListener("DOMContentLoaded", inject, { once: true });
void storageGet(["csScoutShip", "csScoutFees"]);
