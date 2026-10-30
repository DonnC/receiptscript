import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { ReceiptScript } from '@receiptscript/core';
import { HtmlRenderer } from '@receiptscript/core/html';

const directory = new URL('../receipt-showcase/', import.meta.url);
const source = readFileSync(new URL('showcase.rct', directory), 'utf8');
const data = JSON.parse(readFileSync(new URL('showcase.preview.json', directory), 'utf8'));
const receipt = new ReceiptScript().compile(source).render(data);
const html = receipt.render(new HtmlRenderer({
  resolveImage(reference) {
    const image = readFileSync(new URL(reference, directory));
    return `data:image/png;base64,${image.toString('base64')}`;
  }
}));
const output = new URL('../../artifacts/', import.meta.url);
mkdirSync(output, {recursive: true});
writeFileSync(new URL('showcase.html', output), html);
console.log(`Rendered ${receipt.blocks.length} blocks to ${fileURLToPath(new URL('showcase.html', output))}`);
