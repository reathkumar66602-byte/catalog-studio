export type CategoryOption = {
  id: string;
  name: string;
  path: string;
  label?: string;
  n?: number | boolean;
  source?: string;
  _l?: string;
  _s?: string;
};

export const CATEGORY_SHOW_MAX = 60;

type IndexedCategory = CategoryOption & { _l: string; _s: string };

let indexed: IndexedCategory[] = [];
let wordIndex: Record<string, Array<{ i: number; p: number }>> = {};

export function setCategoryCatalog(list: CategoryOption[]) {
  indexed = (list || []).map((item) => {
    const label = String(item.label || item.name || "").trim();
    const path = String(item.path || label).trim();
    const _l = label.toLowerCase();
    return {
      ...item,
      id: String(item.id || label),
      name: label,
      label,
      path,
      _l,
      _s: `${_l} ${path.toLowerCase()}`,
    };
  });
  wordIndex = {};
  indexed.forEach((cat, i) => {
    const seen: Record<string, 1> = {};
    cat._l.split(/[^a-z0-9]+/).forEach((word, pos) => {
      if (word.length < 3 || seen[word]) return;
      seen[word] = 1;
      (wordIndex[word] || (wordIndex[word] = [])).push({ i, p: pos });
    });
    String(cat.path || "")
      .toLowerCase()
      .split(/[^a-z0-9]+/)
      .forEach((word, pos) => {
        if (word.length < 3 || seen[word]) return;
        seen[word] = 1;
        (wordIndex[word] || (wordIndex[word] = [])).push({ i, p: 50 + pos });
      });
  });
}

export function categoryCatalogSize() {
  return indexed.length;
}

function editDist(a: string, b: string, max: number, bLen?: number) {
  const la = a.length;
  const lb = bLen === undefined ? b.length : Math.min(bLen, b.length);
  if (Math.abs(la - lb) > max) return -1;
  let prev = Array.from({ length: lb + 1 }, (_, j) => j);
  let cur = new Array(lb + 1);
  for (let i = 1; i <= la; i++) {
    cur[0] = i;
    let best = i;
    for (let j = 1; j <= lb; j++) {
      const d = Math.min(
        prev[j] + 1,
        cur[j - 1] + 1,
        prev[j - 1] + (a.charCodeAt(i - 1) === b.charCodeAt(j - 1) ? 0 : 1),
      );
      cur[j] = d;
      if (d < best) best = d;
    }
    if (best > max) return -1;
    const t = prev;
    prev = cur;
    cur = t;
  }
  return prev[lb] <= max ? prev[lb] : -1;
}

function prefixLen(a: string, b: string) {
  const n = Math.min(a.length, b.length);
  let i = 0;
  while (i < n && a.charCodeAt(i) === b.charCodeAt(i)) i++;
  return i;
}

function fuzzy(words: string[]) {
  const q = words[0] || "";
  if (q.length < 4) return [] as IndexedCategory[];
  const max = q.length >= 6 ? 2 : 1;
  const q0 = q.charCodeAt(0);
  const q1 = q.charCodeAt(1);

  const pass = (strict: boolean) => {
    const hitW: Record<string, number> = {};
    for (const w of Object.keys(wordIndex)) {
      if (strict) {
        const c0 = w.charCodeAt(0);
        if (c0 !== q0 && c0 !== q1) continue;
      }
      let d = editDist(q, w, max);
      if (d < 0 && w.length > q.length) d = editDist(q, w, max, q.length);
      if (d >= 0) hitW[w] = d;
    }
    const byCat: Record<number, { d: number; pos: number; pre: number; cat: IndexedCategory }> = {};
    const out: number[] = [];
    for (const w of Object.keys(hitW)) {
      const refs = wordIndex[w] || [];
      const dw = hitW[w];
      const pre = prefixLen(q, w);
      for (const ref of refs) {
        const cur = byCat[ref.i];
        if (!cur) {
          byCat[ref.i] = { d: dw, pos: ref.p, pre, cat: indexed[ref.i] };
          out.push(ref.i);
        } else if (dw < cur.d || (dw === cur.d && ref.p < cur.pos)) {
          cur.d = dw;
          cur.pos = ref.p;
          cur.pre = pre;
        }
      }
    }
    return out.map((ci) => byCat[ci]);
  };

  let rows = pass(true);
  if (!rows.length) rows = pass(false);
  rows.sort(
    (a, b) =>
      a.d - b.d
      || b.pre - a.pre
      || a.pos - b.pos
      || (a.cat.n ? 0 : 1) - (b.cat.n ? 0 : 1)
      || a.cat._l.length - b.cat._l.length,
  );
  return rows.map((row) => row.cat);
}

export function searchCategories(query: string): { list: IndexedCategory[]; fuzzy: boolean } {
  const words = (query || "").toLowerCase().split(/\s+/).filter(Boolean);
  if (!indexed.length) return { list: [], fuzzy: false };
  if (!words.length) return { list: indexed.slice(), fuzzy: false };

  const w0 = words[0];
  const scored: Array<{ t: number; mine: number; len: number; cat: IndexedCategory }> = [];
  for (const cat of indexed) {
    if (!words.every((word) => cat._s.includes(word))) continue;
    const p = cat._l.indexOf(w0);
    let tier = 3;
    if (p === 0) tier = 0;
    else if (p > 0 && cat._l[p - 1] === " ") tier = 1;
    else if (p > 0) tier = 2;
    scored.push({ t: tier, mine: cat.n ? 0 : 1, len: cat._l.length, cat });
  }
  scored.sort((a, b) => a.t - b.t || a.mine - b.mine || a.len - b.len);
  let list = scored.map((row) => row.cat);
  let usedFuzzy = false;
  const strong = scored.some((row) => row.t <= 1);
  if (!list.length || !strong) {
    const fz = fuzzy(words);
    if (fz.length) {
      if (!list.length) {
        list = fz;
        usedFuzzy = true;
      } else {
        const ids = new Set(list.map((item) => item.id));
        const extra = fz.filter((item) => !ids.has(item.id));
        if (extra.length) {
          list = [...extra, ...list];
          usedFuzzy = true;
        }
      }
    }
  }
  return { list, fuzzy: usedFuzzy };
}

export function highlightCategoryText(label: string, words: string[]) {
  let html = escapeHtml(label);
  for (const word of words) {
    if (!word) continue;
    const re = new RegExp(`(${word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")})`, "ig");
    html = html.replace(re, "<b>$1</b>");
  }
  return html;
}

export function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char] || char);
}
