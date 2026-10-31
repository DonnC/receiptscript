import test from "node:test";
import assert from "node:assert/strict";
import {existsSync, readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";

const root = fileURLToPath(new URL("../../", import.meta.url));
test("Kotlin is the primary module and authoring dependencies stay under tools", () => {
  for (const path of ["core/build.gradle.kts", "tools/package.json", "tools/web/package.json", "tools/vscode-extension/package.json"]) {
    assert.ok(existsSync(root + path), path);
  }
  for (const path of ["package.json", "package-lock.json", "packages", "vscode-extension", "scripts", "test", "kotlin"]) {
    assert.equal(existsSync(root + path), false, path);
  }
  assert.match(readFileSync(root + "settings.gradle.kts", "utf8"), /include\(":core"\)/);
});
