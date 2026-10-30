import {readFile, writeFile} from "node:fs/promises";
import {fileURLToPath} from "node:url";
import {join} from "node:path";

// Published packages reuse the one authored README; repository-relative links become absolute.
const root = fileURLToPath(new URL("..", import.meta.url));
const source = await readFile(join(root, "README.md"), "utf8");
export const packagedReadme = source.replace(/\]\((?!https?:|#)([^)]+)\)/g, (_, path) => {
  const base = /\.(png|svg|jpe?g)$/.test(path)
    ? "https://raw.githubusercontent.com/DonnC/receiptscript/main/"
    : "https://github.com/DonnC/receiptscript/blob/main/";
  return `](${base}${path})`;
});
for (const directory of ["packages/core", "vscode-extension"]) {
  await writeFile(join(root, directory, "README.md"), packagedReadme);
}
