import { RctParseError } from "./errors.js";
import { FILTERS } from "./engine.js";
import { badSpans } from "./lexer.js";
import { parse, parseFragment } from "./parser.js";
import { parseTemplateString } from "./template.js";

/**
 * Structural and binding diagnostics for authoring tools and compilation. Errors are found
 * before adapter execution; diagnostics carry stable codes and localizable parameters.
 *
 * Each issue: {severity: "error" | "warning", code, line, column, message, params}. `code` and
 * `params` let a host show its own translated message; `message` is the English fallback.
 *
 * Example: lint('receipt "x"\ncenter "{{a | shout}}"')
 *   -> [{severity: "error", code: "unknown-filter", line: 2, column: 1,
 *        message: "Unknown filter 'shout'.", params: {filter: "shout"}}]
 */
export function lint(source, { fragment = false } = {}) {
  const issues = [];
  let tree = null;

  try {
    tree = fragment ? parseFragment(source) : parse(source);
  } catch (error) {
    if (!(error instanceof RctParseError)) throw error;
    issues.push(issue("error", "parse", error.line ?? 1, 1, error.message, { detail: error.message }));
  }

  const lines = String(source ?? "").split("\n");
  for (const [start] of badSpans(String(source ?? ""))) {
    const [line, column] = locate(lines, start);
    issues.push(issue("error", "unterminated", line, column, "Unterminated string or malformed interpolation."));
  }

  if (tree) {
    const nodes = fragment ? tree : [...tree.header, ...tree.body, ...tree.footer];
    issues.push(...inspectNodes(nodes));
  }
  return dedupe(issues);
}

/** The render-time checks on an already-parsed tree; the designer runs these as the user edits. */
export function inspectNodes(nodes, { inTable = false } = {}) {
  const issues = [];
  for (const node of nodes) {
    const line = node.line ?? null;
    if (inTable && !["row", "if", "for"].includes(node.type)) {
      issues.push(issue("warning", "ignored-in-table", line, 1,
        `A ${node.type} inside a table is not printed; only rows are.`, { type: node.type }, node));
    }
    for (const raw of templatesOf(node)) issues.push(...inspectTemplate(raw, line, node));
    if (node.type === "if" && !isValidPath(node.path)) {
      issues.push(issue("error", "invalid-path", line, 1, `Invalid binding path: ${node.path}`, { path: node.path }, node));
    }
    if (node.type === "for" && !isValidPath(node.collection)) {
      issues.push(issue("error", "invalid-path", line, 1, `Invalid binding path: ${node.collection}`, { path: node.collection }, node));
    }
    if (node.children?.length) {
      issues.push(...inspectNodes(node.children, { inTable: inTable || node.type === "table" }));
    }
    if (node.children && !hasBody(node)) {
      issues.push(issue("error", "empty-block", line, 1, `A ${node.type} needs at least one block inside it.`, { type: node.type }, node));
    }
    if (node.type === "row" && node.columns.length < 2) {
      issues.push(issue("error", "row-columns", line, 1, "A row needs at least 2 columns.", {}, node));
    }
  }
  return issues;
}

/** The grammar needs an indented line under a block; a table's headers/weights line counts. */
function hasBody(node) {
  if (node.children.length) return true;
  return node.type === "table" && Boolean(node.headers.length || node.weights?.length || node.alignments?.length);
}

export function templatesOf(node) {
  switch (node.type) {
    case "text":
      return [node.text];
    case "kv":
      return [node.key, node.value];
    case "row":
      return node.columns;
    case "table":
      return node.headers;
    case "image":
      return [node.source];
    case "qr":
    case "barcode":
      return [node.content];
    default:
      return [];
  }
}

function inspectTemplate(raw, line, node) {
  let parts;
  try {
    parts = parseTemplateString(raw, line);
  } catch (error) {
    return [issue("error", "parse", line, 1, error.message, { detail: error.message }, node)];
  }
  const issues = [];
  for (const part of parts) {
    if (part.kind !== "binding") continue;
    if (!isValidPath(part.path)) {
      issues.push(issue("error", "invalid-path", line, 1, `Invalid binding path: ${part.path}`, { path: part.path }, node));
    }
    for (const filter of part.filters) {
      if (!FILTERS.includes(filter.name)) {
        issues.push(issue("error", "unknown-filter", line, 1, `Unknown filter '${filter.name}'.`, { filter: filter.name }, node));
      } else if (filter.name === "date" && hasTimeField(filter.args[0])) {
        issues.push(issue("error", "date-time-field", line, 1,
          `The date filter can't show a time; use datetime for '${filter.args[0]}'.`, { pattern: filter.args[0] }, node));
      }
    }
  }
  return issues;
}

const PATH_SEGMENT = /^[@A-Za-z_][@A-Za-z0-9_]*(\[\d+])?$/;

export function isValidPath(path) {
  const trimmed = String(path ?? "").trim();
  if (!trimmed) return false;
  const rest = trimmed === "@root" ? "" : trimmed.startsWith("@root.") ? trimmed.slice(6) : trimmed;
  return rest.split(".").filter((segment) => segment.trim()).every((segment) => PATH_SEGMENT.test(segment));
}

function hasTimeField(arg) {
  const quoted = /^\\?"(.*)\\?"$/.exec(String(arg ?? "").trim());
  if (!quoted) return false;
  const unquotedLetters = quoted[1].replace(/'[^']*'/g, "");
  return /[HhmsSa]/.test(unquotedLetters);
}

function issue(severity, code, line, column, message, params = {}, node = null) {
  const result = { severity, code, line: line ?? 1, column, message, params };
  if (node) Object.defineProperty(result, "node", { value: node, enumerable: false });
  return result;
}

function locate(lines, offset) {
  let remaining = offset;
  for (let index = 0; index < lines.length; index++) {
    if (remaining <= lines[index].length) return [index + 1, remaining + 1];
    remaining -= lines[index].length + 1;
  }
  return [Math.max(lines.length, 1), 1];
}

function dedupe(issues) {
  const seen = new Set();
  return issues.filter((item) => {
    const key = `${item.severity}|${item.code}|${item.line}|${item.column}|${item.message}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
