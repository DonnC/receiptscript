/** Character-grid fallback for SDKs without native column layout. */
export class TextRenderer {
  constructor({columns = 32, media} = {}) {
    if (!Number.isInteger(columns) || columns < 4 || columns > 1000) throw new RangeError("columns must be between 4 and 1000");
    this.columns = columns;
    this.media = media;
  }

  render(receipt) {
    const lines = receipt.blocks.flatMap(block => {
      switch (block.type) {
        case "spacer": return [""];
        case "text":
          if (block.role === "divider") return [fit(block.text, this.columns, "center")];
          return wrap(block.text, this.columns).map(line => fit(line, this.columns, block.align));
        case "kv": {
          const key = Array.from(block.key), value = Array.from(block.value);
          if (key.length + value.length + 1 <= this.columns) return [block.key + " ".repeat(this.columns - key.length - value.length) + block.value];
          return [...wrap(block.key, this.columns), ...wrap(block.value, this.columns).map(line => fit(line, this.columns, "end"))];
        }
        case "table": return tableLines(block, this.columns);
        default:
          if (this.media) return wrap(this.media(block), this.columns);
          throw new Error(`Unsupported block: ${block.type}. Provide a media callback or use a graphics-capable adapter.`);
      }
    });
    return lines.join("\n") + "\n";
  }
}

export function tableLines(table, columns) {
  const count = table.headers.length || table.rows[0]?.length || 0;
  if (!count) return [];
  const available = columns - (count - 1);
  if (available < count) throw new RangeError("Table has more columns than the configured text width can fit");
  const weights = table.weights ?? Array(count).fill(1);
  const sum = weights.reduce((a, b) => a + b, 0);
  // Reserve one character per cell, then distribute the remainder without losing columns.
  const widths = weights.map(weight => 1 + Math.floor((available - count) * weight / sum));
  for (let i = 0, remainder = available - widths.reduce((a, b) => a + b, 0); i < remainder; i++) widths[i % count]++;
  const renderRow = row => {
    const cells = row.map((value, i) => wrap(value, widths[i]));
    return Array.from({length: Math.max(...cells.map(cell => cell.length))}, (_, line) => cells.map((cell, i) => fit(cell[line] ?? "", widths[i], table.alignments?.[i] ?? "center")).join(" "));
  };
  return [...(table.headers.length ? renderRow(table.headers) : []), ...table.rows.flatMap(renderRow)];
}

function wrap(text, width) {
  return String(text).split(/\r?\n/).flatMap(line => {
    const chars = Array.from(line);
    const lines = [];
    while (chars.length > width) {
      const space = chars.slice(0, width + 1).lastIndexOf(" ");
      const cut = space > 0 ? space : width;
      lines.push(chars.splice(0, cut).join(""));
      if (chars[0] === " ") chars.shift();
    }
    lines.push(chars.join(""));
    return lines;
  });
}

function fit(text, width, align) {
  const chars = Array.from(text).slice(0, width);
  const gap = width - chars.length;
  const left = ["end", "right"].includes(align) ? gap : align === "center" ? Math.floor(gap / 2) : 0;
  return " ".repeat(left) + chars.join("") + " ".repeat(gap - left);
}
