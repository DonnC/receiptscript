import type {Receipt, ReceiptAdapter, Block, QrNode, BarcodeNode} from "./index.js";
export class HtmlRenderer implements ReceiptAdapter<string> {
  constructor(options?: {resolveImage?: (source: string) => string | null | undefined});
  render(receipt: Receipt): string;
  block(block: Block): string;
}
export function symbolSvg(block: QrNode | BarcodeNode): string;
