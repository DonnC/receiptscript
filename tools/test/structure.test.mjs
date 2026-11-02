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
  assert.match(readFileSync(root + "example/build.gradle.kts", "utf8"), /implementation\(project\(":"\)\)/);
});
test("Kotlin is the primary module and authoring dependencies stay under tools", () => {
  const core = "src/main/kotlin/io/github/donnclab/rct/";
  const android = "src/main/java/io/github/donnclab/rct/";
  for (const path of [
    // The public surface: one engine object and the three interfaces an app implements.
    android + "ReceiptScript.kt", android + "ReceiptDriver.kt", android + "ReceiptImageResolver.kt",
    core + "ReceiptTemplateRepository.kt",
    // Platform-independent core, also shipped as the JVM JAR.
    core + "RctCompiler.kt", core + "ReceiptDocument.kt", core + "escpos/EscPosEncoder.kt",
    "tools/package.json", "tools/web/package.json", "tools/vscode-extension/package.json"
  ]) {
    assert.ok(existsSync(root + path), path);
  }
  for (const path of ["package.json", "package-lock.json", "packages", "vscode-extension", "scripts", "test", "kotlin", "core", "drivers", "ui", "examples"]) {
    assert.equal(existsSync(root + path), false, path);
  }
  assert.match(readFileSync(root + "settings.gradle.kts", "utf8"), /include\(":example"\)/);
});

test("Android starter has a shared runnable configuration and a pinned daemon JDK", () => {
  const sample = root + "example/";
  assert.match(readFileSync(root + "gradle/gradle-daemon-jvm.properties", "utf8"), /toolchainVersion=17/);
  const run = readFileSync(root + ".run/ReceiptScript_Starter.run.xml", "utf8");
  assert.match(run, /AndroidRunConfigurationType/);
  assert.match(run, /module name="receiptscript.example"/);
  assert.match(run, /name="MODE" value="default_activity"/);
  assert.ok(existsSync(sample + "src/main/AndroidManifest.xml"));
});

test("open-source project files are present and the changelog covers the current version", () => {
  for (const path of ["README.md", "LICENSE", "CHANGELOG.md", "CONTRIBUTING.md", "CODE_OF_CONDUCT.md", "SECURITY.md",
    "THIRD_PARTY_NOTICES.md", ".github/PULL_REQUEST_TEMPLATE.md", ".github/ISSUE_TEMPLATE/bug_report.yml",
    ".github/ISSUE_TEMPLATE/feature_request.yml", ".github/ISSUE_TEMPLATE/config.yml", ".github/dependabot.yml", "docs/README.md"]) {
    assert.ok(existsSync(root + path), path);
  }
  const version = readFileSync(root + "VERSION", "utf8").trim();
  assert.match(readFileSync(root + "CHANGELOG.md", "utf8"), new RegExp(`^## \\[${version.replaceAll(".", "\\.")}\\]`, "m"));
});
