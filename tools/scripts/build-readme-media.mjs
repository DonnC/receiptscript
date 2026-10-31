import {existsSync} from "node:fs";
import {readFile, mkdir, writeFile} from "node:fs/promises";
import {fileURLToPath} from "node:url";
import {chromium} from "playwright-core";
import {ReceiptScript} from "../web/src/index.js";
import {HtmlRenderer} from "../web/src/html.js";
import {repositoryRoot} from "./paths.mjs";

process.chdir(repositoryRoot);
const source = await readFile("examples/readme/sale.rct", "utf8");
const data = JSON.parse(await readFile("examples/readme/sale.preview.json", "utf8"));
const receipt = new ReceiptScript().compile(source).render(data);
const receiptHtml = new HtmlRenderer().render(receipt);
const escape = text => text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;");
const lines = source.trimEnd().split("\n").map((line, index) =>
  `<div class="line"><span class="number">${index + 1}</span><span>${escape(line).replace(/&quot;.*?&quot;/g, text => `<b>${text}</b>`) || " "}</span></div>`).join("");
const logo = `data:image/svg+xml;base64,${(await readFile("docs/media/logo.svg")).toString("base64")}`;
const html = `<!doctype html><html><head><meta charset="utf-8"><style>
*{box-sizing:border-box}body{margin:0;background:#f5f3ed;color:#112c3e;padding:52px 60px 36px;font-family:Georgia,serif;width:1440px}header{display:flex;align-items:center;gap:14px}header img{width:44px;height:44px}.brand{font-size:23px;font-weight:bold}.eyebrow{font:11px/1.4 Consolas,monospace;letter-spacing:2px;color:#4b7778;margin:28px 0 8px}h1{font-size:40px;font-weight:normal;letter-spacing:-1px;margin:0 0 30px}.grid{display:grid;grid-template-columns:850px 390px;gap:40px;align-items:start}.code{background:#102c3f;color:#e4edf0;border-radius:12px;overflow:hidden;box-shadow:0 8px 30px #102c3f12}.bar{padding:15px 24px;border-bottom:1px solid #ffffff14;display:flex;justify-content:space-between;font:12px Consolas,monospace;color:#b6cbcf}.bar span:last-child{color:#7cb6b6}.source{padding:22px 20px 25px;font:15px/25px Consolas,monospace;white-space:pre}.line{display:flex}.number{width:34px;flex-shrink:0;color:#6e909b;text-align:right;margin-right:20px}b{font-weight:normal;color:#e8c299}.receipt-frame{height:570px;background:white;border:1px solid #e8e5dc;border-radius:8px;overflow:hidden;box-shadow:0 10px 30px #102c3f0b}iframe{width:100%;height:100%;border:0;background:white}.caption{font:13px/1.6 Consolas,monospace;color:#647b80;margin:14px 0 0}.footer{margin-top:28px;border-top:1px solid #dadfd9;padding-top:16px;display:flex;justify-content:space-between;font:12px Consolas,monospace;color:#526f77}
</style></head><body>
<header><img src="${logo}"><div class="brand">ReceiptScript</div></header>
<div class="eyebrow">ONE TEMPLATE / YOUR DATA / YOUR DRIVER</div>
<h1>Readable source. A receipt ready to render.</h1>
<div class="grid">
  <section>
    <div class="code"><div class="bar"><span>sale.rct</span><span>ReceiptScript</span></div><div class="source">${lines}</div></div>
    <p class="caption">A reusable layout, bound to Kotlin objects or maps.</p>
  </section>
  <section>
    <div class="receipt-frame"><iframe title="Rendered receipt" srcdoc="${escape(receiptHtml.replace("background:#f2f3f3", "background:white"))}"></iframe></div>
  </section>
</div>
</body></html>`;
await mkdir("artifacts", {recursive: true});
await writeFile("artifacts/readme-example.html", html);
const candidates = [process.env.RECEIPTSCRIPT_BROWSER,
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "/usr/bin/chromium", "/usr/bin/chromium-browser", "/usr/bin/google-chrome",
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"].filter(Boolean);
const executablePath = candidates.find(existsSync);
if (!executablePath) throw new Error("Set RECEIPTSCRIPT_BROWSER to a Chromium/Chrome/Edge executable to generate README media.");
const browser = await chromium.launch({executablePath, headless: true});
try {
  const page = await browser.newPage({viewport: {width: 1440, height: 1000}, deviceScaleFactor: 1});
  await page.setContent(html, {waitUntil: "load"});
  await page.evaluate(() => document.fonts.ready);
  await mkdir("docs/media", {recursive: true});
  await page.locator("body").screenshot({path: "docs/media/receipt-example.png"});
  console.log(`README media generated from ${receipt.blocks.length} resolved receipt blocks`);
} finally {await browser.close();}
