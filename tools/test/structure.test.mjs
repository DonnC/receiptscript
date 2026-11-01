import test from "node:test";
import assert from "node:assert/strict";
import {existsSync, readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";

const root = fileURLToPath(new URL("../../", import.meta.url));
test("all distributions share the canonical semantic version", () => {
  const version = readFileSync(root + "VERSION", "utf8").trim();
  assert.match(version, /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/);
  for (const path of ["tools/package.json", "tools/web/package.json", "tools/vscode-extension/package.json"]) {
    assert.equal(JSON.parse(readFileSync(root + path, "utf8")).version, version, path);
  }
  const lock = JSON.parse(readFileSync(root + "tools/package-lock.json", "utf8"));
  for (const path of ["", "web", "vscode-extension"]) assert.equal(lock.packages[path].version, version, path);
  assert.match(readFileSync(root + "build.gradle.kts", "utf8"), /file\("VERSION"\)/);
  assert.match(readFileSync(root + "examples/compose-app/build.gradle.kts", "utf8"), /file\("\.\.\/\.\.\/VERSION"\)/);
});
test("Kotlin is the primary module and authoring dependencies stay under tools", () => {
  for (const path of ["core/build.gradle.kts", "tools/package.json", "tools/web/package.json", "tools/vscode-extension/package.json"]) {
    assert.ok(existsSync(root + path), path);
  }
  for (const path of ["package.json", "package-lock.json", "packages", "vscode-extension", "scripts", "test", "kotlin"]) {
    assert.equal(existsSync(root + path), false, path);
  }
  assert.match(readFileSync(root + "settings.gradle.kts", "utf8"), /include\(":core"\)/);
});

test("Android starter has a shared runnable configuration and a pinned daemon JDK", () => {
  const sample = root + "examples/compose-app/";
  assert.match(readFileSync(sample + "gradle/gradle-daemon-jvm.properties", "utf8"), /toolchainVersion=17/);
  const run = readFileSync(sample + ".run/ReceiptScript_Starter.run.xml", "utf8");
  assert.match(run, /AndroidRunConfigurationType/);
  assert.match(run, /module name="ReceiptScriptStarter.app"/);
  assert.match(run, /name="MODE" value="default_activity"/);
  assert.match(readFileSync(sample + "settings.gradle.kts", "utf8"), /include\(":app"\)/);
});
