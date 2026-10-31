import { RctParseError } from "./errors.js";

/**
 * A template string is kept in the syntax tree as the raw text between its quotes, exactly as
 * written, so the writer can put it back untouched. These parse it into literal and binding
 * parts the same way rct-core does when it renders.
 *
 * Example: parseTemplateString("TID: {{terminal.tid | upper}}")
 *   -> [{kind: "literal", text: "TID: "},
 *       {kind: "binding", path: "terminal.tid", filters: [{name: "upper", args: []}]}]
 */
export function parseTemplateString(raw, line = null) {
  const parts = [];
  let cursor = 0;

  while (cursor < raw.length) {
    const start = raw.indexOf("{{", cursor);
    if (start === -1) {
      const literal = raw.slice(cursor);
      if (literal) parts.push({ kind: "literal", text: literal });
      break;
    }
    if (start > cursor) parts.push({ kind: "literal", text: raw.slice(cursor, start) });

    const end = raw.indexOf("}}", start + 2);
    if (end === -1) throw new RctParseError(`Unmatched template binding in string: ${raw}`, line);

    const binding = raw.slice(start + 2, end).trim();
    if (!binding) throw new RctParseError(`Empty template binding in string: ${raw}`, line);
    parts.push(parseBinding(binding, line));
    cursor = end + 2;
  }

  if (!parts.length) parts.push({ kind: "literal", text: "" });
  return parts;
}

export function parseBinding(binding, line = null) {
  const segments = binding.split("|").map((segment) => segment.trim()).filter(Boolean);
  if (!segments.length) throw new RctParseError(`Invalid binding: ${binding}`, line);

  const filters = segments.slice(1).map((segment) => {
    const separator = segment.indexOf(":");
    const name = (separator === -1 ? segment : segment.slice(0, separator)).trim();
    if (!name) throw new RctParseError(`Invalid filter in binding: ${binding}`, line);
    const args = separator === -1
      ? []
      : segment.slice(separator + 1).split(",").map((arg) => arg.trim()).filter(Boolean);
    return { name, args };
  });

  return { kind: "binding", path: segments[0], filters };
}

/** Every binding in a raw template string; [] when it doesn't parse. */
export function bindingsOf(raw) {
  try {
    return parseTemplateString(raw).filter((part) => part.kind === "binding");
  } catch {
    return [];
  }
}

export function formatBinding(path, filters = []) {
  const chain = filters.map(({ name, args }) => (args?.length ? `${name}:${args.join(",")}` : name));
  return `{{${[path, ...chain].join(" | ")}}}`;
}
