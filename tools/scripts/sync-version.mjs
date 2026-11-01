import {readFile, writeFile} from "node:fs/promises";
import {join} from "node:path";
import {repositoryRoot} from "./paths.mjs";

const version = (await readFile(join(repositoryRoot, "VERSION"), "utf8")).trim();
if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(version)) {
  throw new Error("VERSION must contain MAJOR.MINOR.PATCH, without build-specific suffixes.");
}
const check = process.argv.includes("--check");
for (const path of ["tools/package.json", "tools/web/package.json", "tools/vscode-extension/package.json", "tools/package-lock.json"]) {
  const file = join(repositoryRoot, path);
  const source = await readFile(file, "utf8");
  const data = JSON.parse(source);
  const versions = [data];
  if (data.packages) versions.push(...["", "web", "vscode-extension"].map(key => data.packages[key]));
  if (check) {
    if (versions.some(item => item?.version !== version)) throw new Error(`${path} differs from VERSION; run npm run version:sync.`);
  } else {
    versions.forEach(item => { item.version = version; });
    await writeFile(file, JSON.stringify(data, null, 2) + "\n");
  }
}
console.log(`ReceiptScript ${version}: package versions ${check ? "verified" : "synchronized"}.`);
