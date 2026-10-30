import { code128, code39, ean13, ean8, upca, interleaved2of5, qrcode, drawingSVG } from "bwip-js/generic";
import { BARCODE_FORMATS } from "./parser.js";

const escape = value => String(value ?? "").replace(/[&<>"']/g, char => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"}[char]));
const encoders = {code128, code39, ean13, ean8, upca, itf: interleaved2of5};

/** Offline barcode/QR generation. Encoder validation is surfaced rather than drawing fake bars. */
export function symbolSvg(block) {
  const qr = block.type === "qr";
  const format = block.format ?? "code128";
  if (!qr && !BARCODE_FORMATS.includes(format)) throw new Error(`Unsupported barcode format: ${format}`);
  try {
    const svg = (qr ? qrcode : encoders[format])({text: block.content, scale: 2,
      padding: qr ? 8 : 10, backgroundcolor: "FFFFFF", includetext: !qr}, drawingSVG());
    const width = qr ? block.size : block.width;
    const height = qr ? block.size : block.height;
    return svg.replace(/<svg\b/, `<svg role="img" aria-label="${qr ? "QR code" : "Barcode"}" style="width:${width}px;height:${height}px;max-width:100%"`);
  } catch (error) {
    throw new Error(`Invalid ${qr ? "QR code" : "barcode"} content: ${error.message}`, {cause: error});
  }
}

/** Returns a self-contained HTML document; callers explicitly resolve image references. */
export class HtmlRenderer {
  constructor({resolveImage = () => null} = {}) { this.resolveImage = resolveImage; }

  render(receipt) {
    const columns = receipt.meta?.width ?? 34;
    const baseFont = receipt.meta?.font ?? 14;
    const content = receipt.blocks.map(block => this.block(block)).join("\n");
    return `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${escape(receipt.name)}</title><style>
body{margin:0;padding:24px;background:#f2f3f3;color:#111;font:14px monospace} .receipt{box-sizing:content-box;width:${columns}ch;font-size:${baseFont}px;max-width:calc(100% - 32px);margin:auto;padding:16px;background:white;line-height:1.4} .text{white-space:pre-wrap;overflow-wrap:anywhere} .kv{display:flex;justify-content:space-between;gap:1ch}.kv span{overflow-wrap:anywhere;min-width:0}.kv span:last-child{text-align:right}table{width:100%;table-layout:fixed;border-collapse:collapse}th,td{padding:2px;vertical-align:top;overflow-wrap:anywhere} .media{text-align:center;margin:8px 0}.media img{max-width:100%;object-fit:contain}.spacer{height:1.4em}svg{display:block;margin:auto}@media print{body{padding:0;background:white}.receipt{margin:0;max-width:none;padding:0}}
</style></head><body><main class="receipt">${content}</main></body></html>`;
  }

  block(block) {
    const style = `font-size:${block.fontSize ?? ({small: 11, middle: 14, big: 17}[block.size] ?? 14)}px;font-weight:${block.bold ? 700 : 400};text-decoration:${block.underline ? "underline" : "none"}`;
    switch (block.type) {
      case "text": return `<div class="text" style="${style};text-align:${["left", "center", "right"].includes(block.align) ? block.align : "left"}">${escape(block.text)}</div>`;
      case "kv": return `<div class="kv" style="${style}"><span>${escape(block.key)}</span><span>${escape(block.value)}</span></div>`;
      case "spacer": return '<div class="spacer"></div>';
      case "table": {
        const count = block.headers.length || block.rows[0]?.length || 0;
        const weights = block.weights ?? Array(count).fill(1);
        const sum = weights.reduce((a, b) => a + b, 0);
        const cell = (value, i, tag) => `<${tag} style="text-align:${({start: "left", center: "center", end: "right"}[block.alignments?.[i]] ?? "center")};font-size:${tag === "th" ? block.headerFontSize ?? 11 : block.rowFontSize ?? 11}px">${escape(value)}</${tag}>`;
        return `<table><colgroup>${weights.map(w => `<col style="width:${w / sum * 100}%">`).join("")}</colgroup>${block.headers.length ? `<thead><tr>${block.headers.map((v, i) => cell(v, i, "th")).join("")}</tr></thead>` : ""}<tbody>${block.rows.map(row => `<tr>${row.map((v, i) => cell(v, i, "td")).join("")}</tr>`).join("")}</tbody></table>`;
      }
      case "qr": case "barcode": return `<div class="media">${symbolSvg(block)}</div>`;
      case "image": {
        const source = this.resolveImage(block.source);
        if (!source) throw new Error(`Unresolved image: ${block.source}`);
        if (!/^(data:image\/(png|jpeg|gif|webp);base64,|https:\/\/|blob:)/i.test(source)) throw new Error("Unsupported image source");
        return `<div class="media"><img src="${escape(source)}" alt="Receipt image"${block.width ? ` width="${block.width}"` : ""}${block.height ? ` height="${block.height}"` : ""}></div>`;
      }
      default: throw new Error(`Unsupported block: ${block.type}`);
    }
  }
}
