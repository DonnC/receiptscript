import {spawnSync} from "node:child_process";
import {readFile, mkdir} from "node:fs/promises";
import {join} from "node:path";
import {repositoryRoot, toolsRoot} from "./paths.mjs";

const version = (await readFile(join(repositoryRoot, "VERSION"), "utf8")).trim();
const artifacts = join(repositoryRoot, "artifacts");
await mkdir(artifacts, {recursive: true});
const result = spawnSync(process.execPath, [
  join(toolsRoot, "node_modules/@vscode/vsce/vsce"), "package", "--no-dependencies",
  "--out", join(artifacts, `receiptscript-${version}.vsix`)
], {cwd: join(toolsRoot, "vscode-extension"), stdio: "inherit"});
if (result.error) throw result.error;
if (result.status !== 0) throw new Error(`Extension packaging failed: ${result.status}`);
