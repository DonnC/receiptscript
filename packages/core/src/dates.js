/**
 * The slice of java.time that rct-core's `date`/`datetime` filters reach: parsing ISO local,
 * offset and date-only strings, epoch milliseconds, and `DateTimeFormatter` patterns. Values are
 * wall-clock parts ({year, month, day, hour, minute, second, nano}), like a `LocalDateTime`.
 */

const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August",
  "September", "October", "November", "December"];
const DAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

const ISO_LOCAL = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?$/;
const ISO_OFFSET = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?(Z|[+-]\d{2}:?\d{2})$/;
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

export function toLocalDateTime(value, inputPattern) {
  if (value instanceof Date) return fromDate(value);
  if (typeof value === "number") return fromDate(new Date(Math.trunc(value)));
  if (typeof value !== "string") return null;
  return (inputPattern && parseWithPattern(value, inputPattern)) || parseIso(value);
}

function parseIso(raw) {
  // OffsetDateTime.toLocalDateTime() keeps the wall time as written, so the offset is dropped.
  const match = ISO_LOCAL.exec(raw) || ISO_OFFSET.exec(raw);
  if (match) {
    return validated({
      year: +match[1], month: +match[2], day: +match[3], hour: +match[4], minute: +match[5],
      second: +(match[6] ?? 0), nano: +((match[7] ?? "").padEnd(9, "0") || 0),
    });
  }
  const date = ISO_DATE.exec(raw);
  return date ? validated({ year: +date[1], month: +date[2], day: +date[3], hour: 0, minute: 0, second: 0, nano: 0 }) : null;
}

function fromDate(date) {
  return {
    year: date.getFullYear(), month: date.getMonth() + 1, day: date.getDate(), hour: date.getHours(),
    minute: date.getMinutes(), second: date.getSeconds(), nano: date.getMilliseconds() * 1e6,
  };
}

function validated(parts) {
  const probe = new Date(parts.year, parts.month - 1, parts.day);
  const ok = probe.getMonth() === parts.month - 1 && probe.getDate() === parts.day
    && parts.hour < 24 && parts.minute < 60 && parts.second < 60;
  return ok ? parts : null;
}

/** Splits a DateTimeFormatter pattern into letter runs and literals ('quoted' or not). */
function tokenize(pattern) {
  const tokens = [];
  let index = 0;
  while (index < pattern.length) {
    const char = pattern[index];
    if (char === "'") {
      const end = pattern.indexOf("'", index + 1);
      const text = end === -1 ? pattern.slice(index + 1) : pattern.slice(index + 1, end);
      tokens.push({ literal: text === "" ? "'" : text });
      index = end === -1 ? pattern.length : end + 1;
    } else if (/[A-Za-z]/.test(char)) {
      let end = index;
      while (pattern[end] === char) end++;
      tokens.push({ letter: char, count: end - index });
      index = end;
    } else {
      tokens.push({ literal: char });
      index++;
    }
  }
  return tokens;
}

const TIME_LETTERS = new Set(["H", "h", "m", "s", "S", "a"]);

/** `dateOnly` mirrors formatting a LocalDate: java.time refuses any time-of-day field. */
export function formatPattern(parts, pattern, dateOnly = false) {
  return tokenize(pattern).map((token) => {
    if (token.literal !== undefined) return token.literal;
    if (dateOnly && TIME_LETTERS.has(token.letter)) {
      throw new RangeError(`Unsupported field for a date: '${token.letter}' in "${pattern}"`);
    }
    return field(parts, token);
  }).join("");
}

function field(parts, { letter, count }) {
  const pad = (value, width) => String(value).padStart(width, "0");
  switch (letter) {
    case "y":
    case "u":
      return count === 2 ? pad(parts.year % 100, 2) : pad(parts.year, count);
    case "M":
    case "L":
      if (count >= 4) return MONTHS[parts.month - 1];
      if (count === 3) return MONTHS[parts.month - 1].slice(0, 3);
      return pad(parts.month, count);
    case "d":
      return pad(parts.day, count);
    case "H":
      return pad(parts.hour, count);
    case "h":
      return pad(parts.hour % 12 || 12, count);
    case "m":
      return pad(parts.minute, count);
    case "s":
      return pad(parts.second, count);
    case "S":
      return pad(Math.floor(parts.nano / 1e9 * 10 ** count), count);
    case "a":
      return parts.hour < 12 ? "AM" : "PM";
    case "E": {
      const day = DAYS[new Date(parts.year, parts.month - 1, parts.day).getDay()];
      return count >= 4 ? day : day.slice(0, 3);
    }
    default:
      throw new RangeError(`Unsupported date pattern letter '${letter}'`);
  }
}

const NUMERIC_WIDTHS = { y: 4, u: 4, M: 2, d: 2, H: 2, h: 2, m: 2, s: 2 };

function parseWithPattern(raw, pattern) {
  let tokens;
  try {
    tokens = tokenize(pattern);
  } catch {
    return null;
  }
  const parts = { year: null, month: null, day: null, hour: 0, minute: 0, second: 0, nano: 0 };
  let pm = null;
  let cursor = 0;

  for (const token of tokens) {
    if (token.literal !== undefined) {
      if (!raw.startsWith(token.literal, cursor)) return null;
      cursor += token.literal.length;
      continue;
    }
    const { letter, count } = token;
    if ((letter === "M" || letter === "L") && count >= 3) {
      const names = MONTHS.map((month) => (count === 3 ? month.slice(0, 3) : month));
      const index = names.findIndex((name) => raw.startsWith(name, cursor));
      if (index === -1) return null;
      parts.month = index + 1;
      cursor += names[index].length;
      continue;
    }
    if (letter === "a") {
      const marker = raw.slice(cursor, cursor + 2).toUpperCase();
      if (marker !== "AM" && marker !== "PM") return null;
      pm = marker === "PM";
      cursor += 2;
      continue;
    }
    const width = letter === "S" ? count : count === 2 && (letter === "y" || letter === "u") ? 2 : Math.max(count, NUMERIC_WIDTHS[letter] ?? count);
    const digits = raw.slice(cursor, cursor + width);
    if (!/^\d+$/.test(digits) || digits.length !== width) return null;
    cursor += width;
    const value = Number(digits);
    if (letter === "y" || letter === "u") parts.year = width === 2 ? 2000 + value : value;
    else if (letter === "M" || letter === "L") parts.month = value;
    else if (letter === "d") parts.day = value;
    else if (letter === "H" || letter === "h") parts.hour = value;
    else if (letter === "m") parts.minute = value;
    else if (letter === "s") parts.second = value;
    else if (letter === "S") parts.nano = value * 10 ** (9 - width);
    else return null;
  }

  if (cursor !== raw.length || parts.year === null || parts.month === null || parts.day === null) return null;
  if (pm !== null) parts.hour = (parts.hour % 12) + (pm ? 12 : 0);
  return validated(parts);
}
