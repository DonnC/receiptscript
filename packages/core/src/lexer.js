/**
 * Finds unterminated strings and interpolations. Grammar validation belongs to the parser;
 * dotted condition paths are valid and must not become stray-character diagnostics.
 */

export function badSpans(source) {
  const spans = [];
  let index = 0;

  while (index < source.length) {
    const start = index;
    const current = source[index];

    if (/\s/.test(current)) {
      while (index < source.length && /\s/.test(source[index])) index++;
    } else if (current === "#") {
      while (index < source.length && source[index] !== "\n") index++;
    } else if (source.startsWith("{{", index)) {
      index = consumeInterpolation(source, index);
      if (!source.slice(start, index).endsWith("}}")) spans.push([start, index]);
    } else if (current === '"') {
      index = consumeString(source, start, spans);
    } else if (/\d/.test(current)) {
      while (index < source.length && /[\d.]/.test(source[index])) index++;
    } else if (isIdentifierStart(current)) {
      while (index < source.length && isIdentifierPart(source[index])) index++;
    } else {
      index++;
    }
  }
  return spans;
}

function consumeInterpolation(source, start) {
  const end = source.indexOf("}}", start + 2);
  return end === -1 ? source.length : end + 2;
}

function consumeString(source, start, spans) {
  let index = start + 1;
  while (index < source.length) {
    if (source[index] === "\\" && index + 1 < source.length) {
      index += 2;
    } else if (source.startsWith("{{", index)) {
      const end = consumeInterpolation(source, index);
      if (!source.slice(index, end).endsWith("}}")) spans.push([index, end]);
      index = end;
    } else if (source[index] === '"') {
      return index + 1;
    } else {
      index++;
    }
  }
  spans.push([start, source.length]);
  return source.length;
}

function isIdentifierStart(char) {
  return /\p{L}/u.test(char) || char === "_" || char === "@";
}

function isIdentifierPart(char) {
  return /[\p{L}\p{N}]/u.test(char) || char === "_" || char === "@";
}
