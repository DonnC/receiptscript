import { RctParseError } from "./errors.js";
import { formatPattern, toLocalDateTime } from "./dates.js";
import { parseTemplateString } from "./template.js";

/**
 * Binding resolution, truthiness, and display filters. Resolve own object properties only;
 * loop locals override root keys. Decimal formatting rounds digits rather than binary floats.
 */

export const FILTERS = Object.freeze([
  "upper", "lower", "default", "maskPan", "mask", "money", "date", "datetime",
  "padLeft", "padRight", "padCenter",
]);

const DEFAULT_DATE_PATTERN = "dd/MM/yyyy";
const DEFAULT_DATETIME_PATTERN = "dd/MM/yyyy HH:mm:ss";
const PATH_SEGMENT = /^([@A-Za-z_][@A-Za-z0-9_]*)(\[(\d+)])?$/;
const DECIMAL = /^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$/;
const INTEGER = /^[+-]?\d+$/;

/** `root` is the receipt's data; `locals` are loop variables, which win over root keys. */
export function createContext(root, locals = {}, missingBindings = "empty") {
  return { root: root ?? null, locals, missingBindings };
}

export function withLocals(context, locals) {
  return { ...context, locals: { ...context.locals, ...locals } };
}

const templateCache = new Map();

export function render(raw, context) {
  let parts = templateCache.get(raw);
  if (!parts) {
    parts = parseTemplateString(raw);
    templateCache.set(raw, parts);
    if (templateCache.size > 512) templateCache.delete(templateCache.keys().next().value);
  }
  let output = "";
  for (const part of parts) {
    if (part.kind === "literal") {
      output += part.text;
      continue;
    }
    const value = resolveBinding(part, context);
    if (value != null) output += stringify(value);
  }
  return output;
}

export function resolveValue(path, context) {
  const trimmed = String(path ?? "").trim();
  if (!trimmed) return null;

  if (trimmed === "@root") return context.root;
  if (trimmed.startsWith("@root.")) return resolveSegments(context.root, parsePath(trimmed.slice(6)));
  if (hasOwn(context.locals, trimmed)) return context.locals[trimmed];

  const segments = parsePath(trimmed);
  if (!segments.length) return null;

  const [first, ...rest] = segments;
  let current = null;
  if (hasOwn(context.locals, first.name)) current = context.locals[first.name];
  else if (context.root != null) current = descend(context.root, first.name);

  if (first.index !== null) current = indexInto(current, first.index);
  return resolveSegments(current, rest);
}

export function isTruthy(path, context) {
  return truthy(resolveValue(path, context));
}

export function truthy(value) {
  if (value == null) return false;
  if (typeof value === "boolean") return value;
  if (typeof value === "string") return value.trim().length > 0;
  if (Array.isArray(value)) return value.length > 0;
  if (typeof value === "number") return value !== 0;
  if (typeof value === "object") return Object.keys(value).length > 0;
  return true;
}

/** Kotlin `toString()` of the value a binding produced. */
export function stringify(value) {
  if (value == null) return "";
  if (Array.isArray(value)) return `[${value.map(stringify).join(", ")}]`;
  if (typeof value === "object") {
    return `{${Object.entries(value).map(([key, item]) => `${key}=${stringify(item)}`).join(", ")}}`;
  }
  return String(value);
}

function resolveBinding(part, context) {
  let value = resolveValue(part.path, context);
  if (value == null && context.missingBindings === "error" && !part.filters.some(f => f.name === "default")) {
    throw new RctParseError(`Missing binding: ${part.path}`);
  }
  for (const filter of part.filters) value = applyFilter(filter, value, context);
  return value;
}

function applyFilter({ name, args }, value, context) {
  switch (name) {
    case "upper":
      return stringify(value).toUpperCase();
    case "lower":
      return stringify(value).toLowerCase();
    case "default":
      return truthy(value) ? value : resolveArg(args[0], context);
    case "maskPan":
      return maskPan(stringify(value));
    case "mask":
      return mask(stringify(value), args, context);
    case "money":
      return formatMoney(value, args[0] === undefined ? null : resolveArg(args[0], context));
    case "date":
      return formatDate(value, args, context, DEFAULT_DATE_PATTERN, false);
    case "datetime":
      return formatDate(value, args, context, DEFAULT_DATETIME_PATTERN, true);
    case "padLeft":
      return pad(stringify(value), args, context, "left");
    case "padRight":
      return pad(stringify(value), args, context, "right");
    case "padCenter":
      return pad(stringify(value), args, context, "center");
    default:
      throw new RctParseError(`Unsupported filter: ${name}`);
  }
}

function formatMoney(value, currency) {
  const amount = typeof value === "number" ? numberToDecimal(value)
    : typeof value === "string" && DECIMAL.test(value) ? value
    : null;
  if (amount === null) return stringify(value);
  const code = stringify(currency).trim() ? stringify(currency) : "";
  const normalized = roundHalfUp(amount, 2);
  return code ? `${code} ${normalized}` : normalized;
}

function formatDate(value, args, context, defaultPattern, withTime) {
  const outputPattern = datePattern(args[0], context) ?? defaultPattern;
  const inputPattern = datePattern(args[1], context);
  const parts = toLocalDateTime(value, inputPattern);
  if (!parts) return stringify(value);
  try {
    return formatPattern(parts, outputPattern, !withTime);
  } catch (error) {
    throw new RctParseError(error.message);
  }
}

function datePattern(arg, context) {
  const resolved = resolveArg(arg, context);
  const text = resolved == null ? "" : String(resolved);
  return text.trim() ? text : null;
}

function maskPan(raw) {
  const digits = raw.replace(/\D/g, "");
  if (digits.length < 10) return raw;
  return `${digits.slice(0, 6)}******${digits.slice(-4)}`;
}

function mask(raw, args, context) {
  if (!raw) return raw;
  const showFirst = Math.max(0, toInt(resolveArg(args[0], context)) ?? 0);
  const showLast = Math.max(0, toInt(resolveArg(args[1], context)) ?? 0);
  const maskArg = resolveArg(args[2], context);
  const maskChar = maskArg != null && String(maskArg).length ? String(maskArg)[0] : "*";

  const visible = showFirst + showLast;
  if (visible <= 0 || visible >= raw.length) return maskChar.repeat(raw.length);
  return raw.slice(0, showFirst) + maskChar.repeat(raw.length - visible) + raw.slice(raw.length - showLast);
}

function pad(input, args, context, direction) {
  const target = toInt(resolveArg(args[0], context));
  if (target === null) return input;
  const padArg = resolveArg(args[1], context);
  const padChar = padArg != null && String(padArg).length ? String(padArg)[0] : " ";
  if (input.length >= target) return input;

  const missing = target - input.length;
  if (direction === "left") return padChar.repeat(missing) + input;
  if (direction === "right") return input + padChar.repeat(missing);
  const left = Math.floor(missing / 2);
  return padChar.repeat(left) + input + padChar.repeat(missing - left);
}

/** A filter argument: "quoted" text, a number, a data path, or else the bare word itself. */
function resolveArg(arg, context) {
  if (arg == null) return null;
  const trimmed = arg.trim();
  if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) return trimmed.slice(1, -1);
  if (trimmed.length >= 4 && trimmed.startsWith('\\"') && trimmed.endsWith('\\"')) return trimmed.slice(2, -2);
  if (INTEGER.test(trimmed)) return Number(trimmed);
  if (DECIMAL.test(trimmed)) return Number(trimmed);
  return resolveValue(trimmed, context) ?? trimmed;
}

function toInt(value) {
  if (value == null) return null;
  const text = String(value);
  return INTEGER.test(text) ? Number(text) : null;
}

function parsePath(path) {
  return path.split(".").filter((segment) => segment.trim()).map((segment) => {
    const match = PATH_SEGMENT.exec(segment);
    if (!match) throw new RctParseError(`Invalid binding path: ${path}`);
    return { name: match[1], index: match[3] !== undefined ? Number(match[3]) : null };
  });
}

function resolveSegments(current, segments) {
  let value = current;
  for (const segment of segments) {
    value = descend(value, segment.name);
    if (segment.index !== null) value = indexInto(value, segment.index);
    if (value == null) return null;
  }
  return value;
}

function descend(current, key) {
  if (current == null) return null;
  if (Array.isArray(current)) return key === "empty" ? current.length === 0 : null;
  if (typeof current === "object") return hasOwn(current, key) ? current[key] ?? null : null;
  if (typeof current === "string") {
    if (key === "empty") return current.length === 0;
    if (key === "blank") return current.trim().length === 0;
  }
  return null;
}

function indexInto(current, index) {
  return Array.isArray(current) ? current[index] ?? null : null;
}

function hasOwn(target, key) {
  return target != null && typeof target === "object" && Object.prototype.hasOwnProperty.call(target, key);
}

/** Double.toString() as BigDecimal.valueOf sees it: plain digits, no exponent. */
function numberToDecimal(value) {
  if (!Number.isFinite(value)) return null;
  const text = String(value);
  if (!/e/i.test(text)) return text;
  const [mantissa, exponentText] = text.toLowerCase().split("e");
  const exponent = Number(exponentText);
  const negative = mantissa.startsWith("-");
  const [whole, fraction = ""] = mantissa.replace("-", "").split(".");
  const digits = whole + fraction;
  const point = whole.length + exponent;
  const plain = point <= 0
    ? `0.${"0".repeat(-point)}${digits}`
    : point >= digits.length
      ? digits + "0".repeat(point - digits.length)
      : `${digits.slice(0, point)}.${digits.slice(point)}`;
  return (negative ? "-" : "") + plain;
}

/** BigDecimal.setScale(scale, HALF_UP).toPlainString(), done on the digits to avoid float error. */
function roundHalfUp(decimal, scale) {
  let text = decimal.trim();
  if (/e/i.test(text)) text = numberToDecimal(Number(text));
  const negative = text.startsWith("-");
  text = text.replace(/^[+-]/, "");
  let [whole, fraction = ""] = text.split(".");
  whole = whole || "0";
  const kept = (fraction + "0".repeat(scale)).slice(0, scale);
  const roundUp = (fraction[scale] ?? "0") >= "5";

  let digits = (whole + kept).split("").map(Number);
  if (roundUp) {
    let index = digits.length - 1;
    while (index >= 0) {
      if (digits[index] === 9) {
        digits[index] = 0;
        index--;
      } else {
        digits[index]++;
        break;
      }
    }
    if (index < 0) digits = [1, ...digits];
  }
  const joined = digits.join("");
  const integerPart = joined.slice(0, joined.length - scale).replace(/^0+(?=\d)/, "");
  const result = scale ? `${integerPart}.${joined.slice(-scale)}` : integerPart;
  const isZero = /^[0.]+$/.test(result);
  return negative && !isZero ? `-${result}` : result;
}
