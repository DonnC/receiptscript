import {ReceiptScript, type ReceiptAdapter, type Receipt} from "@receiptscript/core";
import {HtmlRenderer} from "@receiptscript/core/html";
import {TextRenderer} from "@receiptscript/core/text";
const engine = new ReceiptScript({templates: {sale: 'receipt "sale"\nleft "{{name}}"'}});
const receipt = engine.template("sale").render({name: "Example"});
const html: string = receipt.render(new HtmlRenderer());
const text: string = receipt.render(new TextRenderer({columns: 32}));
class Bytes implements ReceiptAdapter<Uint8Array> {
  render(receipt: Receipt): Uint8Array {return new TextEncoder().encode(receipt.name);}
}
const bytes: Uint8Array = receipt.render(new Bytes());
void [html, text, bytes];
