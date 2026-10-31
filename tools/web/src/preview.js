import { DEFAULT_WIDTH, fontPolicyOf, resolveFontSize } from "./layout.js";

/**
 * How editor tooling draws the neutral model on screen. These are preview conventions, not
 * printer facts: a vendor adapter maps the same model onto its own fonts and dots.
 *
 * - A line is `meta.width` characters of the middle font, so a full-width divider or row fills
 *   it exactly; everything else is laid out inside that line.
 * - Style sizes without `meta.font` are the shared preview theme's (small 11, middle 13, big 16);
 *   with it, the font policy decides, as it does on the terminal.
 * - Image, QR and barcode sizes are printer dots across `meta.paper`, scaled to the line.
 */

export const STYLE_FONT_PX = Object.freeze({ small: 11, middle: 13, big: 16 });
/** Advance width of a monospace glyph, in em. */
export const MONOSPACE_ADVANCE_EM = 0.6;
export const LINE_HEIGHT = 1.45;
export const DEFAULT_PAPER = "58mm";
/** Printable dots across common roll widths at 203 dpi. */
export const PAPER_DOTS = Object.freeze({ "58mm": 384, "80mm": 576 });
const DOTS_PER_MM = 384 / 58;

export function paperDots(paper) {
  const key = String(paper ?? DEFAULT_PAPER).trim().toLowerCase();
  if (PAPER_DOTS[key]) return PAPER_DOTS[key];
  const millimetres = Number.parseFloat(key);
  return Number.isFinite(millimetres) && millimetres > 0 ? Math.round(millimetres * DOTS_PER_MM) : PAPER_DOTS[DEFAULT_PAPER];
}

/**
 * Example: geometry({width: 34, paper: "58mm"})
 *   -> {width: 34, lineWidthPx: 265.2, middlePx: 13, dotScale: 0.69, ...}
 */
export function geometry(meta) {
  const width = meta?.width ?? DEFAULT_WIDTH;
  const policy = fontPolicyOf(meta);
  const fontPx = (style, inline = null) => resolveFontSize(policy, style, inline) ?? STYLE_FONT_PX[style];
  const middlePx = fontPx("middle");
  const lineWidthPx = width * MONOSPACE_ADVANCE_EM * middlePx;
  const dots = paperDots(meta?.paper);
  return {
    width,
    dots,
    middlePx,
    lineWidthPx,
    lineHeightPx: middlePx * LINE_HEIGHT,
    dotScale: lineWidthPx / dots,
    fontPx,
  };
}

/** A block's font in screen pixels: its resolved size, else its style's preview size. */
export function blockFontPx(block, geometryInfo, sizeKey = "size", fontKey = "fontSize") {
  return block[fontKey] ?? STYLE_FONT_PX[block[sizeKey] ?? "middle"] ?? geometryInfo.middlePx;
}

/** Column widths as fractions, the way rct-core's adapters share a line out by weight. */
export function columnFractions(weights, count) {
  if (Array.isArray(weights) && weights.length === count && weights.every((w) => Number.isFinite(w) && w > 0)) {
    const total = weights.reduce((sum, value) => sum + value, 0);
    return weights.map((value) => value / total);
  }
  return Array.from({ length: count }, () => 1 / count);
}

/**
 * An image scaled into its width x height box without stretching; with no box it keeps its own
 * size, capped at the paper width.
 */
export function fitImage(natural, node, geometryInfo) {
  const maxDots = geometryInfo.dots;
  let { width, height } = natural;
  if (node.width != null && node.height != null) {
    const scale = Math.min(node.width / width, node.height / height);
    width *= scale;
    height *= scale;
  } else if (node.width != null) {
    height *= node.width / width;
    width = node.width;
  }
  if (width > maxDots) {
    height *= maxDots / width;
    width = maxDots;
  }
  return { width: width * geometryInfo.dotScale, height: height * geometryInfo.dotScale };
}
