import { build } from "esbuild";
import { copyFileSync, mkdirSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("..", import.meta.url));
process.chdir(root);
mkdirSync("vscode-extension/dist", {recursive: true});
mkdirSync("artifacts", {recursive: true});
copyFileSync("LICENSE", "packages/core/LICENSE");
mkdirSync("vscode-extension/dist/notices", {recursive: true});
for (const name of readdirSync("node_modules/bwip-js").filter(name => name.startsWith("LICENSE"))) {
  copyFileSync(`node_modules/bwip-js/${name}`, `vscode-extension/dist/notices/${name}`);
}
await build({absWorkingDir: root, entryPoints: ["packages/core/src/index.js"], outfile: "vscode-extension/dist/core.cjs", bundle: true, platform: "node", format: "cjs", target: "node20"});
await build({absWorkingDir: root, entryPoints: ["packages/core/src/html.js"], outfile: "vscode-extension/dist/html.cjs", bundle: true, platform: "node", format: "cjs", target: "node20", legalComments: "eof"});
