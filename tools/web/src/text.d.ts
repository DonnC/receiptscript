import type {Receipt, ReceiptAdapter, Block, TableBlock} from "./index.js";
export class TextRenderer implements ReceiptAdapter<string> {
  constructor(options?: {columns?: number; media?: (block: Block) => string});
  render(receipt: Receipt): string;
}
export function tableLines(table: TableBlock, columns: number): string[];
