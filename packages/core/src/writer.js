import { DEFAULT_BARCODE_HEIGHT, DEFAULT_BARCODE_WIDTH, DEFAULT_QR_SIZE } from "./parser.js";

/**
 * Turns a syntax tree back into canonical `.rct` source. `parse(write(tree))` gives the same tree
 * (the round-trip tests hold it to that); comments and hand formatting are not kept, since the
 * tree has no place for them.
 *
 * Example: write({name: "sale", meta: {width: 34}, body: [{type: "spacer", lines: 1}], ...})
 *   -> 'receipt "sale"\n\nmeta:\n  width: 34\n\nspacer 1\n'
 */

const INDENT = "  ";
const META_ORDER = ["width", "paper", "copy", "templateVersion", "font", "fontScaleMode"];
const QUOTED_META = new Set(["paper", "copy", "fontScaleMode"]);

export function write(document) {
  const chunks = [`receipt "${document.name}"`];

  const meta = writeMeta(document.meta);
  if (meta.length) chunks.push(meta.join("\n"));

  const refs = [
    ...document.headerRefs.map((id) => `use header "${id}"`),
    ...document.footerRefs.map((id) => `use footer "${id}"`),
  ];
  if (refs.length) chunks.push(refs.join("\n"));

  // Sections are written only when an inline header or footer needs them; a body-only template
  // stays at the top level, the way most templates are written by hand.
  if (document.header.length || document.footer.length) {
    for (const section of ["header", "body", "footer"]) {
      if (document[section].length) chunks.push([`${section}:`, ...writeNodes(document[section], 1)].join("\n"));
    }
  } else if (document.body.length) {
    chunks.push(writeNodes(document.body, 0).join("\n"));
  }

  return `${chunks.join("\n\n")}\n`;
}

/** A shared header or footer: its statements only. */
export function writeFragment(nodes) {
  return nodes.length ? `${writeNodes(nodes, 0).join("\n")}\n` : "";
}

function writeMeta(meta) {
  if (!meta) return [];
  const entries = [];
  for (const key of META_ORDER) {
    if (meta[key] != null && meta[key] !== "") entries.push([key, meta[key]]);
  }
  for (const [key, value] of Object.entries(meta.extra ?? {})) entries.push([key, value]);
  if (!entries.length) return [];

  return ["meta:", ...entries.map(([key, value]) => {
    const quoted = QUOTED_META.has(key) || (key in (meta.extra ?? {}) && !/^-?\d+(\.\d+)?$/.test(String(value)));
    return `${INDENT}${key}: ${quoted ? `"${value}"` : formatNumber(value)}`;
  })];
}

export function writeNodes(nodes, depth) {
  return nodes.flatMap((node) => writeNode(node, depth));
}

function writeNode(node, depth) {
  const pad = INDENT.repeat(depth);
  switch (node.type) {
    case "text":
      return [`${pad}${node.align}${modifiers(node)} ${quote(node.text)}${fontSuffix(node)}`];
    case "kv":
      return [`${pad}kv${modifiers(node)} ${quote(node.key)} ${quote(node.value)}${fontSuffix(node)}`];
    case "divider": {
      const pattern = node.pattern && node.pattern !== "-" ? ` ${quote(node.pattern)}` : "";
      return [`${pad}divider${pattern}${node.length != null ? ` ${node.length}` : ""}`];
    }
    case "spacer":
      return [`${pad}spacer ${node.lines}`];
    case "row":
      return [`${pad}row ${node.columns.map(quote).join(" ")}`];
    case "table":
      return [
        `${pad}table:`,
        ...(node.headers.length ? [`${pad}${INDENT}headers: ${node.headers.map(quote).join(" ")}`] : []),
        ...(node.weights?.length ? [`${pad}${INDENT}weights: ${node.weights.map(formatNumber).join(" ")}`] : []),
        ...(node.alignments?.length ? [`${pad}${INDENT}alignments: ${node.alignments.join(" ")}`] : []),
        ...writeNodes(node.children, depth + 1),
      ];
    case "image": {
      // Height only means something after a width: the grammar reads the numbers in order.
      const size = node.width != null ? ` ${node.width}${node.height != null ? ` ${node.height}` : ""}` : "";
      return [`${pad}image ${quote(node.source)}${size}`];
    }
    case "qr":
      return [`${pad}qr ${quote(node.content)} ${node.size ?? DEFAULT_QR_SIZE}`];
    case "barcode":
      return [`${pad}barcode ${quote(node.content)} ${node.width ?? DEFAULT_BARCODE_WIDTH} ${node.height ?? DEFAULT_BARCODE_HEIGHT}${node.format && node.format !== "code128" ? ` "${node.format}"` : ""}`];
    case "if":
      return [`${pad}if ${node.path}:`, ...writeNodes(node.children, depth + 1)];
    case "for":
      return [`${pad}for ${node.item} in ${node.collection}:`, ...writeNodes(node.children, depth + 1)];
    default:
      throw new TypeError(`Unknown node type: ${node.type}`);
  }
}

function modifiers(node) {
  const words = [];
  if (node.size === "small" || node.size === "big") words.push(node.size);
  if (node.bold) words.push("bold");
  if (node.underline) words.push("underline");
  return words.length ? ` ${words.join(" ")}` : "";
}

function fontSuffix(node) {
  return node.fontSize != null ? ` ${formatNumber(node.fontSize)}` : "";
}

function quote(raw) {
  return `"${raw ?? ""}"`;
}

function formatNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? String(number) : String(value);
}
