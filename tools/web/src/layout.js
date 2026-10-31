import { RctParseError } from "./errors.js";
import { parseFragment } from "./parser.js";
import { isTruthy, render, resolveValue, withLocals } from "./engine.js";

/**
 * Resolves syntax nodes to printer-neutral blocks. Dividers and standalone rows become
 * role-tagged text; tables retain their cells and geometry. Origins link output to editor nodes.
 */

export const DEFAULT_WIDTH = 34;
export const RELATIVE_FONT_STEP = 3;
/** Blank lines between the body and a defined footer. */
export const FOOTER_GAP_LINES = 2;

/**
 * Shared headers precede the inline header; shared footers follow the inline footer.
 * Missing references are reported to the caller, which decides whether to abort rendering.
 */
export function composeFragments(document, loadFragment) {
  const missing = [];
  const resolve = (ids, section) => ids.flatMap((id) => {
    const source = loadFragment ? loadFragment(id) : null;
    if (source == null) {
      missing.push({ section, id });
      return [];
    }
    return parseFragment(source).map((node, index) => tagShared(node, section, id, index));
  });

  const sharedHeader = resolve(document.headerRefs, "header");
  const sharedFooter = resolve(document.footerRefs, "footer");
  return {
    document: {
      ...document,
      header: [...sharedHeader, ...document.header],
      footer: [...document.footer, ...sharedFooter],
      headerRefs: [],
      footerRefs: [],
    },
    missing,
  };
}

function tagShared(node, section, id, index) {
  Object.defineProperty(node, "shared", { value: { section, id, index }, enumerable: false });
  return node;
}

export function fontPolicyOf(meta) {
  return { base: meta?.font ?? null, mode: meta?.fontScaleMode ?? "relative" };
}

/** rct-core's FontPolicy.resolve: null means "the adapter's own size for this style". */
export function resolveFontSize(policy, style, inlineSize) {
  if (inlineSize != null) return inlineSize;
  if (policy.base == null) return null;
  if (policy.mode === "fixed") return policy.base;
  if (style === "small") return Math.max(1, policy.base - RELATIVE_FONT_STEP);
  if (style === "big") return policy.base + RELATIVE_FONT_STEP;
  return policy.base;
}

/**
 * Example: layout(parse(source), createContext({merchant: {name: "Example"}})).
 */
export function layout(document, context) {
  const width = document.meta?.width ?? DEFAULT_WIDTH;
  const env = { width, policy: fontPolicyOf(document.meta) };
  const blocks = [
    ...renderNodes(document.header, context, env, ["header"]),
    ...renderNodes(document.body, context, env, ["body"]),
  ];
  if (document.footer.length) {
    for (let line = 0; line < FOOTER_GAP_LINES; line++) blocks.push({ type: "spacer", origin: null });
    blocks.push(...renderNodes(document.footer, context, env, ["footer"]));
  }
  return blocks;
}

export function renderNodes(nodes, context, env, path) {
  const blocks = [];
  nodes.forEach((node, index) => {
    const origin = originOf(node, [...path, index]);
    blocks.push(...renderNode(node, context, env, origin));
  });
  return blocks;
}

function renderNode(node, context, env, origin) {
  const { width, policy } = env;
  switch (node.type) {
    case "text":
      return [{
        type: "text", origin, text: render(node.text, context), align: node.align, size: node.size,
        fontSize: resolveFontSize(policy, node.size, node.fontSize), bold: node.bold, underline: node.underline,
      }];
    case "kv":
      return [{
        type: "kv", origin, key: render(node.key, context), value: render(node.value, context), size: node.size,
        fontSize: resolveFontSize(policy, node.size, node.fontSize), bold: node.bold, underline: node.underline,
      }];
    case "divider":
      return [{
        type: "text", origin, role: "divider", text: dividerText(node, width), align: "center", size: "middle",
        fontSize: resolveFontSize(policy, "middle", null), bold: false, underline: false,
      }];
    case "spacer":
      return Array.from({ length: node.lines }, () => ({ type: "spacer", origin }));
    case "row":
      return [{
        type: "text", origin, role: "row", text: rowText(node.columns.map((column) => render(column, context)), width),
        align: "left", size: "middle", fontSize: resolveFontSize(policy, "middle", null), bold: false, underline: false,
      }];
    case "table": {
      const rows = tableRows(node.children, context);
      if (!rows.length && !node.headers.length) return [];
      return [{
        type: "table", origin, headers: node.headers.map((header) => render(header, context)), rows,
        weights: node.weights, alignments: node.alignments, lineWidth: width,
        headerSize: "small", headerFontSize: resolveFontSize(policy, "small", null), headerBold: true,
        rowSize: "small", rowFontSize: resolveFontSize(policy, "small", null),
      }];
    }
    case "qr":
      return [{ type: "qr", origin, content: render(node.content, context), size: node.size }];
    case "barcode":
      return [{ type: "barcode", origin, content: render(node.content, context), width: node.width, height: node.height, format: node.format ?? "code128" }];
    case "image":
      return [{ type: "image", origin, source: render(node.source, context), width: node.width, height: node.height }];
    case "if":
      return isTruthy(node.path, context) ? renderNodes(node.children, context, env, origin.path) : [];
    case "for":
      return loopContexts(node, context).flatMap((itemContext) => renderNodes(node.children, itemContext, env, origin.path));
    default:
      throw new RctParseError(`Unsupported node type: ${node.type}`);
  }
}

function originOf(node, path) {
  return node.shared ? { path, shared: node.shared } : { path };
}

/** One context per item, with the same loop variables rct-core sets. */
export function loopContexts(node, context) {
  const items = resolveValue(node.collection, context);
  if (!Array.isArray(items)) return [];
  return items.map((item, index) => withLocals(context, {
    [node.item]: item,
    "@index": index,
    "@item": item,
    "@first": index === 0,
    "@last": index === items.length - 1,
    "@count": items.length,
  }));
}

/** Only rows (directly, or under if/for) become table rows; anything else in a table is dropped. */
export function tableRows(nodes, context) {
  const rows = [];
  for (const node of nodes) {
    if (node.type === "row") rows.push(node.columns.map((column) => render(column, context)));
    else if (node.type === "if" && isTruthy(node.path, context)) rows.push(...tableRows(node.children, context));
    else if (node.type === "for") {
      for (const itemContext of loopContexts(node, context)) rows.push(...tableRows(node.children, itemContext));
    }
  }
  return rows;
}

export function dividerText(node, width) {
  return repeatPattern(node.pattern, node.length ?? width);
}

export function repeatPattern(pattern, targetLength) {
  if (targetLength <= 0) return "";
  const safe = pattern && pattern.trim() ? pattern : "-";
  let output = "";
  while (output.length < targetLength) output += safe;
  return output.slice(0, targetLength);
}

/** rct-core's renderResolvedRow: equal fixed-width cells, each cut and padded, across `width`. */
export function rowText(values, width) {
  const count = values.length;
  if (!count) return "";
  const base = Math.floor(width / count);
  const remainder = width % count;
  return values.map((value, index) => {
    const cellWidth = base + (index < remainder ? 1 : 0);
    return (value ?? "").slice(0, cellWidth).padEnd(cellWidth);
  }).join("").slice(0, width);
}
