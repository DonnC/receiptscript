import { parse } from "./parser.js";
import { createContext } from "./engine.js";
import { composeFragments, layout } from "./layout.js";
import { lint, inspectNodes } from "./lint.js";

/** Entry point for source strings or host-owned template catalogs. No file or network access. */
export class ReceiptScript {
  constructor({ templates = {}, loadTemplate, missingBindings = "error" } = {}) {
    if (!["error", "empty"].includes(missingBindings)) throw new TypeError("Invalid missingBindings policy");
    this.loadTemplate = loadTemplate ?? (id => Object.hasOwn(templates, id) ? templates[id] : null);
    this.missingBindings = missingBindings;
  }

  compile(source) {
    const errors = lint(source).filter(issue => issue.severity === "error");
    if (errors.length) throw new Error(errors.map(issue => `Line ${issue.line}: ${issue.message}`).join("\n"));
    return new CompiledTemplate(parse(source), this);
  }

  template(id) {
    const source = this.loadTemplate(id);
    if (typeof source !== "string") throw new Error(`Unknown template: ${id}`);
    return this.compile(source);
  }
}

/** Reuse a compiled template across transactions. Runtime data is never cached. */
export class CompiledTemplate {
  constructor(document, engine) {
    this.document = document;
    this.engine = engine;
  }

  render(data) {
    const {document, missing} = composeFragments(this.document, this.engine.loadTemplate);
    if (missing.length) throw new Error(missing.map(ref => `Missing ${ref.section} fragment: ${ref.id}`).join("\n"));
    const issues = inspectNodes([...document.header, ...document.body, ...document.footer]).filter(issue => issue.severity === "error" || issue.code === "ignored-in-table");
    if (issues.length) throw new Error(issues.map(issue => issue.message).join("\n"));
    const blocks = layout(document, createContext(data, {}, this.engine.missingBindings));
    validateBlocks(blocks);
    return new Receipt(document.name, document.meta, blocks);
  }
}

/** A resolved receipt can be shared by a screen preview, exporter, and printer adapter. */
export class Receipt {
  constructor(name, meta, blocks) {
    this.name = name;
    this.meta = meta;
    this.blocks = blocks;
  }

  render(adapter) {
    return adapter.render(this);
  }
}

function validateBlocks(blocks) {
  for (const block of blocks) {
    if (block.type === "table") {
      const count = block.headers.length || block.rows[0]?.length || 0;
      if (block.rows.some(row => row.length !== count) || block.weights && block.weights.length !== count || block.alignments && block.alignments.length !== count) {
        throw new Error("Table headers, rows, weights and alignments must have matching columns");
      }
      if (block.weights?.some(w => !Number.isFinite(w) || w <= 0)) throw new Error("Table weights must be positive finite numbers");
    }
    for (const key of ["size", "width", "height", "fontSize"]) {
      if (block[key] != null && typeof block[key] === "number" && (!Number.isFinite(block[key]) || block[key] <= 0)) {
        throw new Error(`Invalid ${key} on ${block.type}`);
      }
    }
  }
}
