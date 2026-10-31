import {build} from "esbuild";
await build({
  stdin: {
    contents: "import {ReceiptScript} from '@receiptscript/core'; import {HtmlRenderer} from '@receiptscript/core/html'; console.log(ReceiptScript, HtmlRenderer);",
    resolveDir: process.cwd()
  },
  bundle: true,
  platform: "browser",
  write: false
});
console.log("Browser bundle passed");
