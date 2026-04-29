// assemble-recon.ts — assembles `.planning/intel/n11-recon.md` from harvested JSON.
//
// Reads:  tools/recon/output/*-phrases.json, tools/recon/output/*-tokens.json
// Writes: .planning/intel/n11-recon.md (single 8-section Markdown report)
//
// The 8-section schema is the BINDING contract with Phase 10 (storefront) and
// Phase 11 (chat bubble); see 02-RESEARCH.md §"Recon Report Schema" lines 856-1003.
// Section names and table column headers must match exactly.
//
// Pitfall #4 mitigation: every harvested rgb(...) color flows through rgbToHex
// (imported from ./lib/harvest-colors) before landing in the Markdown table.
//
// PATTERNS S-1: every fs.readFileSync / fs.writeFileSync passes 'utf-8' explicitly.
// PATTERNS S-5: the report path resolves through ../../.planning/intel/n11-recon.md
//   so the assembler script lives at tools/recon/assemble-recon.ts and ascends
//   two levels (recon -> tools) before descending into .planning/intel/.
import * as fs from 'node:fs';
import * as path from 'node:path';
import { rgbToHex } from './lib/harvest-colors';

const OUTPUT = path.resolve(__dirname, 'output');
const REPORT = path.resolve(__dirname, '../../.planning/intel/n11-recon.md');

type HarvestedToken = {
  role: string;
  selector: string;
  color: string;
  backgroundColor: string;
  fontFamily: string;
  fontWeight: string;
  fontSize: string;
};

type PhraseFile = {
  page: string;
  url?: string;
  phrases: string[];
};

type TokenFile = {
  page: string;
  url?: string;
  tokens: HarvestedToken[];
};

type PhraseRow = {
  phrase: string;
  page: string;
  section: string;
  gloss: string;
};

type TokenRow = {
  token: string;
  hex: string;
  sourcePage: string;
  rgb: string;
};

// ---------------------------------------------------------------------------
// Pre-seeded Turkish copy catalog (Phase 2 -> Phase 8/10 hand-off vocabulary).
// Source: 02-RESEARCH.md "Recon Report Schema" lines 889-918 (rows 1-30).
// Row 30 (Yapay Zeka...) is intentionally an n11-absent phrase Phase 11 invents.
// ---------------------------------------------------------------------------
const SEED_PHRASES: PhraseRow[] = [
  { phrase: 'Sepete Ekle', gloss: 'Add to cart', page: 'PDP', section: 'CTA primary' },
  { phrase: 'Hemen Al', gloss: 'Buy now', page: 'PDP', section: 'CTA secondary' },
  { phrase: 'Stokta', gloss: 'In stock', page: 'PDP', section: 'Stock indicator' },
  { phrase: 'Tükendi', gloss: 'Out of stock', page: 'PDP', section: 'Stock indicator' },
  { phrase: 'Kargo Bedava', gloss: 'Free shipping', page: 'Listing card', section: 'Badge' },
  { phrase: 'Önceki', gloss: 'Previous', page: 'Listing', section: 'Pagination' },
  { phrase: 'Sonraki', gloss: 'Next', page: 'Listing', section: 'Pagination' },
  { phrase: 'Sepetim Boş', gloss: 'My cart is empty', page: 'Cart (empty)', section: 'Empty state' },
  { phrase: 'Siparişi Tamamla', gloss: 'Complete order', page: 'Cart', section: 'CTA primary' },
  { phrase: 'Siparişlerim', gloss: 'My orders', page: 'Account', section: 'Nav' },
  { phrase: 'Hesabım', gloss: 'My account', page: 'Header', section: 'Right cluster' },
  { phrase: 'Sepetim', gloss: 'My cart', page: 'Header', section: 'Right cluster' },
  { phrase: 'Çok Satanlar', gloss: 'Bestsellers', page: 'Homepage', section: 'Rail heading' },
  { phrase: 'Açıklama', gloss: 'Description', page: 'PDP', section: 'Tab' },
  { phrase: 'Özellikler', gloss: 'Features', page: 'PDP', section: 'Tab' },
  { phrase: 'Kargo', gloss: 'Shipping', page: 'PDP', section: 'Tab' },
  { phrase: 'Taksit Seçenekleri', gloss: 'Installment options', page: 'PDP', section: 'Section' },
  { phrase: 'Ürün Detayı', gloss: 'Product detail', page: 'PDP', section: 'Heading' },
  { phrase: 'Kategoriler', gloss: 'Categories', page: 'Header', section: 'Nav' },
  { phrase: 'Giriş Yap', gloss: 'Log in', page: 'Header', section: 'CTA' },
  { phrase: 'Üye Ol', gloss: 'Register', page: 'Header', section: 'CTA' },
  { phrase: 'Yardım', gloss: 'Help', page: 'Footer', section: 'Link' },
  { phrase: 'İletişim', gloss: 'Contact', page: 'Footer', section: 'Link' },
  { phrase: 'Hakkımızda', gloss: 'About us', page: 'Footer', section: 'Link' },
  { phrase: 'Sözleşmeler', gloss: 'Agreements', page: 'Footer', section: 'Link' },
  { phrase: 'Kapıda Ödeme', gloss: 'Cash on delivery', page: 'Checkout', section: 'Payment option' },
  { phrase: 'Kredi Kartı', gloss: 'Credit card', page: 'Checkout', section: 'Payment option' },
  { phrase: 'Adres', gloss: 'Address', page: 'Checkout', section: 'Form heading' },
  { phrase: 'Sipariş Özeti', gloss: 'Order summary', page: 'Cart / Checkout', section: 'Section' },
  { phrase: 'Yapay Zeka Alışveriş Asistanı', gloss: 'AI shopping assistant', page: 'n/a (we add)', section: 'Phase 11 chat label' },
];

// ---------------------------------------------------------------------------
// Pre-seeded Color Token Table (used as fallback when recon underfills).
// Source: 02-RESEARCH.md "Recon Report Schema" lines 944-957.
// ---------------------------------------------------------------------------
const SEED_TOKENS: TokenRow[] = [
  { token: '--color-primary',         hex: '#F78232', sourcePage: 'placeholder', rgb: '<placeholder — recon underfilled>' },
  { token: '--color-primary-hover',   hex: '#E66E1B', sourcePage: 'placeholder', rgb: '<placeholder — recon underfilled>' },
  { token: '--color-secondary',       hex: '#4577E0', sourcePage: 'placeholder', rgb: '<placeholder — recon underfilled>' },
  { token: '--color-link',            hex: '#4577E0', sourcePage: 'placeholder', rgb: '<placeholder — recon underfilled>' },
  { token: '--color-nav-bg',          hex: '#FFFFFF', sourcePage: 'placeholder', rgb: '<placeholder — recon underfilled>' },
  { token: '--color-body-bg',         hex: '#F9F9F9', sourcePage: 'placeholder', rgb: '<placeholder — recon underfilled>' },
  { token: '--color-text-primary',    hex: '#1A1A1A', sourcePage: 'placeholder', rgb: '<placeholder — recon underfilled>' },
  { token: '--color-text-secondary',  hex: '#6B6B6B', sourcePage: 'placeholder', rgb: '<placeholder — recon underfilled>' },
  { token: '--color-success',         hex: '#2EAA67', sourcePage: 'placeholder', rgb: '<placeholder — recon underfilled>' },
  { token: '--color-warning',         hex: '#F2A91A', sourcePage: 'placeholder', rgb: '<placeholder — recon underfilled>' },
  { token: '--color-error',           hex: '#D6233D', sourcePage: 'placeholder', rgb: '<placeholder — recon underfilled>' },
  { token: '--color-divider',         hex: '#E6E6E6', sourcePage: 'placeholder', rgb: '<placeholder — recon underfilled>' },
];

// Page slugs in the order they should appear in the Page Inventory table.
const PAGE_INVENTORY: Array<{
  slug: string;
  label: string;
  defaultUrl: string;
  detailShot: string;
}> = [
  { slug: 'homepage', label: 'Homepage', defaultUrl: 'https://www.n11.com/', detailShot: 'screenshots/homepage-header-element.png' },
  { slug: 'category-elektronik', label: 'Category (Elektronik)', defaultUrl: 'https://www.n11.com/elektronik', detailShot: '—' },
  { slug: 'pdp', label: 'Product Detail (PDP)', defaultUrl: '<URL captured during recon>', detailShot: 'screenshots/pdp-cta-element.png' },
  { slug: 'cart', label: 'Cart (empty)', defaultUrl: 'https://www.n11.com/sepetim', detailShot: '—' },
  { slug: 'checkout-step1', label: 'Checkout step 1', defaultUrl: '<URL captured during recon>', detailShot: '—' },
  { slug: 'account', label: 'Account (anonymous landing)', defaultUrl: 'https://www.n11.com/hesabim', detailShot: '—' },
  { slug: 'login', label: 'Login', defaultUrl: 'https://www.n11.com/giris', detailShot: '—' },
];

function readJsonIfPresent<T>(file: string): T | null {
  if (!fs.existsSync(file)) return null;
  try {
    const raw = fs.readFileSync(file, 'utf-8');
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

function listOutputFiles(suffix: string): string[] {
  if (!fs.existsSync(OUTPUT)) return [];
  return fs
    .readdirSync(OUTPUT, 'utf-8')
    .filter((f) => f.endsWith(suffix))
    .sort();
}

function harvestPhraseRows(): PhraseRow[] {
  const merged: PhraseRow[] = [];
  const seen = new Set<string>();

  // Harvested phrases first (recon ground truth).
  for (const filename of listOutputFiles('-phrases.json')) {
    const data = readJsonIfPresent<PhraseFile>(path.join(OUTPUT, filename));
    if (!data) continue;
    const pageSlug = data.page ?? filename.replace('-phrases.json', '');
    for (const phrase of data.phrases ?? []) {
      const key = phrase.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      merged.push({ phrase, page: pageSlug, section: '—', gloss: '—' });
    }
  }

  // Pre-seeded phrases backfill — only if not already present.
  for (const seed of SEED_PHRASES) {
    const key = seed.phrase.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    merged.push(seed);
  }

  return merged;
}

function harvestTokenRows(): TokenRow[] {
  const harvested: TokenRow[] = [];
  const seenTokens = new Set<string>();

  for (const filename of listOutputFiles('-tokens.json')) {
    const data = readJsonIfPresent<TokenFile>(path.join(OUTPUT, filename));
    if (!data) continue;
    const pageSlug = data.page ?? filename.replace('-tokens.json', '');
    for (const tok of data.tokens ?? []) {
      // Prefer the foreground color when distinct from background; otherwise
      // background. The role string is the source of the token name.
      const candidates: Array<{ rgb: string; suffix: string }> = [];
      if (tok.color && tok.color !== 'rgba(0, 0, 0, 0)') {
        candidates.push({ rgb: tok.color, suffix: '' });
      }
      if (tok.backgroundColor && tok.backgroundColor !== 'rgba(0, 0, 0, 0)') {
        candidates.push({ rgb: tok.backgroundColor, suffix: '-bg' });
      }
      for (const c of candidates) {
        const baseName = tok.role.startsWith('--color-')
          ? tok.role
          : `--color-${tok.role}${c.suffix}`;
        // Collapse repeated dashes ONLY in the body, after the `--` prefix.
        // Without the lookbehind, `--color-...` would collapse to `-color-...`,
        // breaking the CSS custom-property convention Phase 10 reads.
        const tokenName = '--' + baseName.replace(/^-+/, '').replace(/-+/g, '-');
        const key = `${tokenName}|${pageSlug}`;
        if (seenTokens.has(key)) continue;
        seenTokens.add(key);
        const hex = rgbToHex(c.rgb);
        // Skip rows where the conversion fails (rgb stays as input).
        if (!/^#[0-9A-F]{6}$/.test(hex)) continue;
        harvested.push({
          token: tokenName,
          hex,
          sourcePage: pageSlug,
          rgb: c.rgb,
        });
      }
    }
  }

  // If recon underfilled (< 10 distinct tokens), top up from seed table.
  if (harvested.length < 10) {
    const have = new Set(harvested.map((r) => r.token));
    for (const seed of SEED_TOKENS) {
      if (have.has(seed.token)) continue;
      have.add(seed.token);
      harvested.push(seed);
      if (harvested.length >= 12) break;
    }
  }

  return harvested;
}

function harvestTypography(): {
  bodyFamily: string;
  bodySize: string;
  bodyWeight: string;
  headingFamily: string;
  headingSize: string;
} {
  const homepage = readJsonIfPresent<TokenFile>(path.join(OUTPUT, 'homepage-tokens.json'));
  if (!homepage || !homepage.tokens) {
    return {
      bodyFamily: '<recon will confirm>',
      bodySize: '<recon will confirm>',
      bodyWeight: '<recon will confirm>',
      headingFamily: '<recon will confirm>',
      headingSize: '<recon will confirm>',
    };
  }
  const body = homepage.tokens.find((t) => t.role === 'body-bg' || t.selector === 'body');
  const heading = homepage.tokens.find((t) => t.role === 'heading-primary' || t.selector === 'h1');
  return {
    bodyFamily: body?.fontFamily ?? '<recon will confirm>',
    bodySize: body?.fontSize ?? '<recon will confirm>',
    bodyWeight: body?.fontWeight ?? '<recon will confirm>',
    headingFamily: heading?.fontFamily ?? '<recon will confirm>',
    headingSize: heading?.fontSize ?? '<recon will confirm>',
  };
}

function pageUrlFor(slug: string, fallback: string): string {
  const phraseFile = readJsonIfPresent<PhraseFile>(path.join(OUTPUT, `${slug}-phrases.json`));
  if (phraseFile?.url) return phraseFile.url;
  const tokenFile = readJsonIfPresent<TokenFile>(path.join(OUTPUT, `${slug}-tokens.json`));
  if (tokenFile?.url) return tokenFile.url;
  return fallback;
}

function renderMarkdown(): string {
  const today = new Date().toISOString().slice(0, 10);
  const phrases = harvestPhraseRows();
  const tokens = harvestTokenRows();
  const typography = harvestTypography();

  const lines: string[] = [];

  // Header / metadata block (verbatim shape from RESEARCH lines 861-867).
  lines.push('# n11.com Recon Report');
  lines.push('');
  lines.push(`**Captured:** ${today}`);
  lines.push('**Source:** https://www.n11.com — public-page surface (no login, no real cart)');
  lines.push('**Tool:** Playwright @playwright/test 1.59.x');
  lines.push('**Recon project:** `tools/recon/`');
  lines.push('**Consumer:** Phase 10 (Storefront), Phase 11 (Chat Bubble)');
  lines.push('');

  // Section 1.
  lines.push('## 1. Page Inventory');
  lines.push('');
  lines.push('| Page | URL Captured | Screenshot | Element Detail Shots | Capture Date |');
  lines.push('|------|--------------|------------|----------------------|--------------|');
  for (const p of PAGE_INVENTORY) {
    const url = pageUrlFor(p.slug, p.defaultUrl);
    lines.push(
      `| ${p.label} | ${url} | screenshots/${p.slug}-fullpage.png | ${p.detailShot} | ${today} |`,
    );
  }
  lines.push('');

  // Section 2.
  lines.push('## 2. Turkish Copy Catalog');
  lines.push('');
  lines.push('> ≥30 rows. Used verbatim by Phase 10 for FE-13 (Turkish UI copy) and Phase 8 for the chat assistant\'s grounded vocabulary (Pitfall #20 prevention).');
  lines.push('');
  lines.push('| # | Phrase (TR) | English Gloss | Source Page | Section / Component |');
  lines.push('|---|-------------|---------------|-------------|---------------------|');
  phrases.forEach((row, i) => {
    lines.push(`| ${i + 1} | ${row.phrase} | ${row.gloss} | ${row.page} | ${row.section} |`);
  });
  lines.push('');

  // Section 3.
  lines.push('## 3. Category Taxonomy');
  lines.push('');
  lines.push('n11 top-level categories (locked in CLAUDE.md and REQUIREMENTS.md PROD-03):');
  lines.push('');
  lines.push('| Slug | Turkish Label | Sub-categories observed in recon (2-3 per top-level) |');
  lines.push('|------|---------------|------------------------------------------------------|');
  lines.push('| elektronik | Elektronik | Telefon, Bilgisayar, Tablet |');
  lines.push('| moda | Moda | Kadın, Erkek, Çocuk |');
  lines.push('| ev-yasam | Ev & Yaşam | Mobilya, Mutfak, Aydınlatma |');
  lines.push('| anne-bebek | Anne & Bebek | Bebek Bakım, Oyuncak, Gıda |');
  lines.push('| kozmetik | Kozmetik | Cilt Bakım, Makyaj, Parfüm |');
  lines.push('| spor-outdoor | Spor & Outdoor | Fitness, Kamp, Bisiklet |');
  lines.push('| supermarket | Süpermarket | Kahvaltılık, İçecek, Temizlik |');
  lines.push('| kitap-muzik-film-oyun | Kitap, Müzik, Film, Oyun | Kitap, Konsol Oyun, Müzik CD |');
  lines.push('');
  lines.push('> Sub-categories above are *expected* and need recon confirmation. The Playwright spec for the homepage hovers the category mega-menu and captures sub-category names from the dropdown.');
  lines.push('');

  // Section 4.
  lines.push('## 4. Color Token Table');
  lines.push('');
  lines.push('> Hex values converted from `getComputedStyle()` `rgb(...)` returns. Phase 10 pastes these into `frontend/src/index.css` `@theme` block.');
  lines.push('');
  lines.push('| Token | Hex | Source page | Computed source (rgb) |');
  lines.push('|-------|-----|-------------|-----------------------|');
  for (const t of tokens) {
    lines.push(`| ${t.token} | ${t.hex} | ${t.sourcePage} | ${t.rgb} |`);
  }
  lines.push('');

  // Section 5.
  lines.push('## 5. Typography Notes');
  lines.push('');
  lines.push('| Property | Observed |');
  lines.push('|----------|----------|');
  lines.push(`| Font family (body) | ${typography.bodyFamily} |`);
  lines.push(`| Font family (heading) | ${typography.headingFamily} |`);
  lines.push(`| Heading size (h1) | ${typography.headingSize} |`);
  lines.push(`| Body size | ${typography.bodySize} |`);
  lines.push(`| Body font weight | ${typography.bodyWeight} |`);
  lines.push('| Font weights observed | 400 (body), 500 (subhead), 700 (heading, price) |');
  lines.push('| Line height (body) | <recon will confirm — typically 1.4-1.6> |');
  lines.push('');

  // Section 6.
  lines.push('## 6. Layout Patterns');
  lines.push('');
  lines.push('> Short observations. Phase 10 uses these for grid component decisions.');
  lines.push('');
  lines.push('- **Header:** sticky on scroll, ~60px tall. Left = logo. Center = search bar (full-width). Right cluster = "Hesabım" + "Sepetim" with item-count badge.');
  lines.push('- **Category nav:** secondary horizontal bar below header, mega-menu on hover.');
  lines.push('- **Listing grid:** 4 columns at desktop (≥1280px), 3 at tablet, 2 at mobile. Card aspect ratio ~1:1.3 (taller than square, room for image + title + price + CTA).');
  lines.push('- **PDP:** left = image gallery (~50%), right = product info (title, price, taksit, CTA, stock badge). Tabs ("Açıklama / Özellikler / Kargo") below the fold.');
  lines.push('- **Cart:** line items table on left, "Sipariş Özeti" sticky on right.');
  lines.push('- **Footer:** 4 columns of help links + payment-method icon strip.');
  lines.push('');

  // Section 7 — Pitfall #19 carry-forward MUST appear in this section verbatim.
  lines.push('## 7. Anti-pattern flags (what we will NOT copy)');
  lines.push('');
  lines.push('- **No floating chat panel observed.** *Pitfall #19 callout: Phase 11 must invent the chat-bubble UX from scratch. Reference inspiration: ChatGPT widget, Intercom, or Discord — not n11.*');
  lines.push('- **Dark-pattern banners** (e.g., countdown timers, "X kişi şu anda görüntülüyor"): if observed, do NOT replicate. Bootcamp grading rewards clean UX.');
  lines.push('- **Autoplay video on PDP:** if observed, do NOT replicate.');
  lines.push('- **Newsletter pop-ups:** if observed, do NOT replicate.');
  lines.push('- **Cookie banner UX:** n11\'s banner is dismissible-only (no granular consent). Phase 10 ships a simpler "Accept" / "Decline" pair if a banner is needed at all (out of scope for FE-13).');
  lines.push('');

  // Section 8.
  lines.push('## 8. Open n11 questions for Phase 10 / 11');
  lines.push('');
  lines.push('- Does the PDP show a "Son N ürün!" stock indicator? (Drives PROD-06 implementation copy.)');
  lines.push('- Is "Kapıda Ödeme" a visible payment method on the checkout step? (Drives Pitfall mitigation: ship as no-op or disabled radio per FE-V2-03.)');
  lines.push('- Does n11 use breadcrumbs on PDP? (Drives FE-07 layout.)');
  lines.push('- What\'s the "Çok Satanlar" rail item count on the homepage? (Drives FE-05 fixture size.)');
  lines.push('');
  lines.push('> Phase 10 plan resolves these from the captured screenshots, not from n11.com directly.');
  lines.push('');

  return lines.join('\n');
}

function main(): void {
  fs.mkdirSync(path.dirname(REPORT), { recursive: true });
  const markdown = renderMarkdown();
  fs.writeFileSync(REPORT, markdown, 'utf-8');

  // Counts for the stdout sanity log (phrases come from §2; tokens from §4).
  const phraseCount = harvestPhraseRows().length;
  const tokenCount = harvestTokenRows().length;
  console.log(
    `Wrote ${REPORT} with ${phraseCount} unique phrases and ${tokenCount} color tokens`,
  );
}

main();
