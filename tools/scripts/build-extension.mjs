import { build } from "esbuild";
import { copyFileSync, mkdirSync, readdirSync } from "node:fs";
import "./prepare-package-readmes.mjs";
import {repositoryRoot, toolsRoot as root} from "./paths.mjs";
import {join} from "node:path";
process.chdir(root);
mkdirSync("vscode-extension/dist", {recursive: true});
mkdirSync(join(repositoryRoot, "artifacts"), {recursive: true});
copyFileSync(join(repositoryRoot, "LICENSE"), "web/LICENSE");
mkdirSync("vscode-extension/dist/notices", {recursive: true});
for (const name of readdirSync("node_modules/bwip-js").filter(name => name.startsWith("LICENSE"))) {
  copyFileSync(`node_modules/bwip-js/${name}`, `vscode-extension/dist/notices/${name}`);
}
await build({absWorkingDir: root, entryPoints: ["web/src/index.js"], outfile: "vscode-extension/dist/core.cjs", bundle: true, platform: "node", format: "cjs", target: "node20"});
await build({absWorkingDir: root, entryPoints: ["web/src/html.js"], outfile: "vscode-extension/dist/html.cjs", bundle: true, platform: "node", format: "cjs", target: "node20", legalComments: "eof"});
