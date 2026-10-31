import { RctParseError } from "./errors.js";
import { parseTemplateString } from "./template.js";

/**
 * Parses indentation-based .rct documents into JSON-safe nodes. Template strings remain
 * unbound; a non-enumerable line number supports diagnostics without changing canonical output.
 */

const INDENT_SIZE = 2;

const RECEIPT_DECLARATION = /^receipt\s+"([^"]+)"$/;
const USE_FRAGMENT_STATEMENT = /^use\s+(header|footer)\s+"([^"]+)"$/;
const META_ENTRY = /^([A-Za-z][A-Za-z0-9_]*)\s*:\s*(.+)$/;
const DIVIDER_STATEMENT = /^divider(?:\s+"((?:\\.|[^"])*)")?(?:\s+(\d+))?$/;
const SPACER_STATEMENT = /^spacer\s+(\d+)$/;
const IF_STATEMENT = /^if\s+([A-Za-z0-9_.]+)\s*:$/;
const FOR_STATEMENT = /^for\s+([A-Za-z_][A-Za-z0-9_]*)\s+in\s+([A-Za-z0-9_.]+)\s*:$/;
const KEY_VALUE_STATEMENT =
  /^kv\s+((?:(?:bold|small|big|underline)\s+)*)"((?:\\.|[^"])*)"\s+"((?:\\.|[^"])*)"(?:\s+(\d+(?:\.\d+)?))?$/;
const QR_STATEMENT = /^qr\s+"((?:\\.|[^"])*)"(?:\s+(\d+))?$/;
const BARCODE_STATEMENT = /^barcode\s+"((?:\\.|[^"])*)"(?:\s+(\d+))?(?:\s+(\d+))?(?:\s+"([a-z0-9]+)")?$/;
const IMAGE_STATEMENT = /^image\s+"((?:\\.|[^"])*)"(?:\s+(\d+))?(?:\s+(\d+))?$/;
const TEXT_STATEMENT =
  /^(text|left|center|right)\s+((?:(?:bold|small|big|underline)\s+)*)"((?:\\.|[^"])*)"(?:\s+(\d+(?:\.\d+)?))?$/;
const QUOTED_ARGUMENT = /"((?:\\.|[^"])*)"/g;

const KNOWN_META_KEYS = new Set(["width", "paper", "copy", "templateVersion", "font", "fontScaleMode"]);

export const DEFAULT_QR_SIZE = 300;
export const DEFAULT_BARCODE_WIDTH = 300;
export const DEFAULT_BARCODE_HEIGHT = 60;
export const BARCODE_FORMATS = Object.freeze(["code128", "code39", "ean13", "ean8", "upca", "itf"]);

/**
 * Example: parse('receipt "sale"\n\ncenter bold "{{merchant.merchantName}}"')
 *   -> {name: "sale", meta: null, header: [], body: [{type: "text", align: "center", ...}],
 *       footer: [], headerRefs: [], footerRefs: []}
 */
export function parse(source) {
  const state = new ParserState(toSourceLines(source));
  const declaration = state.next();
  if (!declaration) throw new RctParseError("Receipt declaration is required.", 1);

  const declarationMatch = RECEIPT_DECLARATION.exec(declaration.content.trim());
  if (!declarationMatch) {
    throw new RctParseError(`Invalid receipt declaration at line ${declaration.number}`);
  }

  const meta = parseMeta(state);
  const content = parseDocumentContent(state);
  return { name: declarationMatch[1], meta, ...content };
}

/** A reusable header or footer: bare statements, no declaration, meta or sections. */
export function parseFragment(source) {
  return parseBlock(new ParserState(toSourceLines(source)), 0);
}

function toSourceLines(source) {
  return String(source ?? "")
    .replace(/\r\n/g, "\n")
    .replace(/\r/g, "\n")
    .split("\n")
    .map((raw, index) => {
      if (raw.includes("\t")) {
        throw new RctParseError(`Tabs are not allowed in .rct files. Line ${index + 1}`);
      }
      return new SourceLine(index + 1, raw);
    });
}

function parseMeta(state) {
  const line = state.peek();
  if (!line || line.content.trim() !== "meta:") return null;
  state.next();

  const entries = new Map();
  for (;;) {
    const next = state.peek();
    if (!next) break;
    const indent = next.indentLevel();
    if (indent === 0) break;
    if (indent !== 1) {
      throw new RctParseError(`Meta entries must be indented by 2 spaces. Line ${next.number}`);
    }
    const entry = state.next();
    const match = META_ENTRY.exec(entry.content.trim());
    if (!match) throw new RctParseError(`Invalid meta entry at line ${entry.number}`);
    entries.set(match[1], unquote(match[2].trim()));
  }

  const extra = {};
  for (const [key, value] of entries) {
    if (!KNOWN_META_KEYS.has(key)) extra[key] = value;
  }

  return {
    width: toIntOrNull(entries.get("width")),
    paper: entries.get("paper") ?? null,
    copy: entries.get("copy") ?? null,
    templateVersion: toIntOrNull(entries.get("templateVersion")),
    font: entries.has("font") ? parseFont(entries.get("font")) : null,
    // null means "not written"; the renderer treats it as relative, like rct-core's default.
    fontScaleMode: entries.has("fontScaleMode") ? parseFontScaleMode(entries.get("fontScaleMode")) : null,
    extra,
  };
}

function parseDocumentContent(state) {
  const header = [];
  const body = [];
  const footer = [];
  const headerRefs = [];
  const footerRefs = [];
  const seenSections = new Set();

  for (;;) {
    const next = state.peek();
    if (!next) break;
    if (next.indentLevel() !== 0) {
      throw new RctParseError(`Unexpected indentation at line ${next.number}`);
    }

    const trimmed = next.content.trim();
    const useMatch = USE_FRAGMENT_STATEMENT.exec(trimmed);
    if (useMatch) {
      state.next();
      (useMatch[1] === "header" ? headerRefs : footerRefs).push(useMatch[2]);
      continue;
    }

    if (trimmed === "header:" || trimmed === "body:" || trimmed === "footer:") {
      const section = trimmed.slice(0, -1);
      const nodes = parseSection(state, next, section, seenSections);
      ({ header, body, footer })[section].push(...nodes);
      continue;
    }

    const line = state.next();
    body.push(parseNode(line, state, 0));
  }

  return { header, body, footer, headerRefs, footerRefs };
}

function parseSection(state, line, section, seenSections) {
  if (seenSections.has(section)) {
    throw new RctParseError(`Duplicate ${section} section at line ${line.number}`);
  }
  seenSections.add(section);
  state.next();
  const next = state.peek();
  if (!next || next.indentLevel() !== 1) {
    throw new RctParseError(`Expected indented block after line ${line.number}`);
  }
  return parseBlock(state, 1);
}

function parseBlock(state, indentLevel) {
  const nodes = [];
  for (;;) {
    const next = state.peek();
    if (!next) break;
    const currentIndent = next.indentLevel();
    if (currentIndent < indentLevel) break;
    if (currentIndent > indentLevel) {
      throw new RctParseError(`Unexpected indentation at line ${next.number}`);
    }
    nodes.push(parseNode(state.next(), state, indentLevel));
  }
  return nodes;
}

function parseNode(line, state, indentLevel) {
  const trimmed = line.content.trim();
  const at = (node) => withLine(node, line.number);
  let match;

  if ((match = DIVIDER_STATEMENT.exec(trimmed))) {
    const pattern = match[1] && match[1].trim() ? match[1] : "-";
    return at({ type: "divider", pattern, length: match[2] ? Number(match[2]) : null });
  }

  if ((match = SPACER_STATEMENT.exec(trimmed))) {
    return at({ type: "spacer", lines: Number(match[1]) });
  }

  if ((match = IF_STATEMENT.exec(trimmed))) {
    const children = parseNestedBlock(state, line, indentLevel);
    return at({ type: "if", path: match[1].trim(), children });
  }

  if ((match = FOR_STATEMENT.exec(trimmed))) {
    const children = parseNestedBlock(state, line, indentLevel);
    return at({ type: "for", item: match[1].trim(), collection: match[2].trim(), children });
  }

  if ((match = KEY_VALUE_STATEMENT.exec(trimmed))) {
    const modifiers = parseModifiers(match[1], match[4]);
    return at({
      type: "kv",
      key: templateAt(match[2], line),
      value: templateAt(match[3], line),
      ...modifiers,
    });
  }

  if (trimmed === "table:") return parseTableBlock(state, line, indentLevel);

  if (trimmed.startsWith("row ")) {
    const columns = extractQuotedArguments(trimmed.slice(3).trim(), "row", line.number);
    if (columns.length < 2) {
      throw new RctParseError(`Row statements need at least 2 columns. Line ${line.number}`);
    }
    return at({ type: "row", columns: columns.map((column) => templateAt(column, line)) });
  }

  if ((match = QR_STATEMENT.exec(trimmed))) {
    return at({
      type: "qr",
      content: templateAt(match[1], line),
      size: match[2] ? Number(match[2]) : DEFAULT_QR_SIZE,
    });
  }

  if ((match = BARCODE_STATEMENT.exec(trimmed))) {
    if (match[4] && !BARCODE_FORMATS.includes(match[4])) throw new RctParseError(`Unsupported barcode format: ${match[4]}`, line.number);
    return at({
      type: "barcode",
      format: match[4] ?? "code128",
      content: templateAt(match[1], line),
      width: match[2] ? Number(match[2]) : DEFAULT_BARCODE_WIDTH,
      height: match[3] ? Number(match[3]) : DEFAULT_BARCODE_HEIGHT,
    });
  }

  if ((match = IMAGE_STATEMENT.exec(trimmed))) {
    return at({
      type: "image",
      source: templateAt(match[1], line),
      width: match[2] ? Number(match[2]) : null,
      height: match[3] ? Number(match[3]) : null,
    });
  }

  if ((match = TEXT_STATEMENT.exec(trimmed))) {
    const modifiers = parseModifiers(match[2], match[4]);
    const align = match[1] === "center" ? "center" : match[1] === "right" ? "right" : "left";
    return at({ type: "text", align, text: templateAt(match[3], line), ...modifiers });
  }

  throw new RctParseError(`Unsupported statement at line ${line.number}: ${trimmed}`);
}

function parseNestedBlock(state, line, parentIndentLevel) {
  const next = state.peek();
  if (!next || next.indentLevel() !== parentIndentLevel + 1) {
    throw new RctParseError(`Expected indented block after line ${line.number}`);
  }
  return parseBlock(state, parentIndentLevel + 1);
}

function parseTableBlock(state, line, parentIndentLevel) {
  const tableIndent = parentIndentLevel + 1;
  const next = state.peek();
  if (!next || next.indentLevel() !== tableIndent) {
    throw new RctParseError(`Expected indented block after line ${line.number}`);
  }

  let headers = [];
  let weights = null;
  let alignments = null;
  const children = [];

  for (;;) {
    const peek = state.peek();
    if (!peek) break;
    const indent = peek.indentLevel();
    if (indent < tableIndent) break;
    if (indent > tableIndent) {
      throw new RctParseError(`Unexpected indentation at line ${peek.number}`);
    }

    const current = state.next();
    const trimmed = current.content.trim();

    if (trimmed.startsWith("headers:")) {
      const values = extractQuotedArguments(trimmed.slice("headers:".length).trim(), "headers", current.number);
      headers = values.map((value) => templateAt(value, current));
    } else if (trimmed.startsWith("weights:")) {
      const values = splitWords(trimmed.slice("weights:".length));
      if (!values.length) throw new RctParseError(`Invalid weights statement at line ${current.number}`);
      weights = values.map((value) => {
        const parsed = toFloatOrNull(value);
        if (parsed === null) throw new RctParseError(`Invalid weight '${value}' at line ${current.number}`);
        return parsed;
      });
    } else if (trimmed.startsWith("alignments:")) {
      const values = splitWords(trimmed.slice("alignments:".length));
      if (!values.length) throw new RctParseError(`Invalid alignments statement at line ${current.number}`);
      alignments = values.map((value) => parseColumnAlignment(value, current.number));
    } else {
      children.push(parseNode(current, state, tableIndent));
    }
  }

  return withLine({ type: "table", headers, weights, alignments, children }, line.number);
}

function parseColumnAlignment(value, lineNumber) {
  switch (value.toLowerCase()) {
    case "start":
    case "left":
      return "start";
    case "center":
    case "middle":
      return "center";
    case "end":
    case "right":
      return "end";
    default:
      throw new RctParseError(`Invalid alignment '${value}' at line ${lineNumber}`);
  }
}

function parseModifiers(raw, trailingFontSize) {
  let bold = false;
  let underline = false;
  let size = "middle";
  for (const token of splitWords(raw)) {
    if (token === "bold") bold = true;
    else if (token === "underline") underline = true;
    else if (token === "small") size = "small";
    else if (token === "big") size = "big";
  }
  return { bold, underline, size, fontSize: trailingFontSize ? Number(trailingFontSize) : null };
}

function parseFont(raw) {
  const parsed = toFloatOrNull(raw);
  if (parsed === null || parsed <= 0) {
    throw new RctParseError(`Invalid font '${raw}'. Expected a positive number.`);
  }
  return parsed;
}

function parseFontScaleMode(raw) {
  const value = String(raw).toLowerCase();
  if (value === "relative" || value === "fixed") return value;
  throw new RctParseError(`Invalid fontScaleMode '${raw}'. Expected relative or fixed.`);
}

function extractQuotedArguments(raw, statement, lineNumber) {
  const matches = [...raw.matchAll(QUOTED_ARGUMENT)];
  const remainder = raw.replace(QUOTED_ARGUMENT, "").trim();
  if (!matches.length || remainder) {
    throw new RctParseError(`Invalid ${statement} statement at line ${lineNumber}`);
  }
  return matches.map((match) => match[1]);
}

function templateAt(raw, line) {
  parseTemplateString(raw, line.number);
  return raw;
}

function withLine(node, line) {
  Object.defineProperty(node, "line", { value: line, enumerable: false, writable: true });
  return node;
}

function unquote(value) {
  return value.length >= 2 && value.startsWith('"') && value.endsWith('"') ? value.slice(1, -1) : value;
}

function splitWords(raw) {
  return String(raw ?? "").trim().split(/\s+/).filter(Boolean);
}

// Kotlin's toIntOrNull/toFloatOrNull: the whole string must be the number, nothing else.
function toIntOrNull(value) {
  return value != null && /^[+-]?\d+$/.test(value) ? Number(value) : null;
}

function toFloatOrNull(value) {
  return value != null && /^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?[fFdD]?$/.test(value)
    ? Number.parseFloat(value)
    : null;
}

class SourceLine {
  constructor(number, content) {
    this.number = number;
    this.content = content;
  }

  isMeaningful() {
    const trimmed = this.content.trim();
    return trimmed.length > 0 && !trimmed.startsWith("#");
  }

  indentLevel() {
    const spaces = this.content.length - this.content.replace(/^ +/, "").length;
    if (spaces % INDENT_SIZE !== 0) {
      throw new RctParseError(`Indentation must use multiples of 2 spaces. Line ${this.number}`);
    }
    return spaces / INDENT_SIZE;
  }
}

class ParserState {
  constructor(lines) {
    this.lines = lines;
    this.index = 0;
  }

  peek() {
    for (let cursor = this.index; cursor < this.lines.length; cursor++) {
      if (this.lines[cursor].isMeaningful()) return this.lines[cursor];
    }
    return null;
  }

  next() {
    while (this.index < this.lines.length) {
      const line = this.lines[this.index++];
      if (line.isMeaningful()) return line;
    }
    return null;
  }
}
