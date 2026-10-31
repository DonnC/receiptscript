import {createHash} from "node:crypto";
import {createWriteStream} from "node:fs";
import {copyFile, mkdir, mkdtemp, readFile, readdir, stat, writeFile} from "node:fs/promises";
import {homedir} from "node:os";
import {delimiter, dirname, join, resolve} from "node:path";
import {spawnSync} from "node:child_process";
import yazl from "yazl";
import {packagedReadme} from "./prepare-package-readmes.mjs";

import {repositoryRoot as root} from "./paths.mjs";
process.chdir(root);
const {version} = JSON.parse(await readFile("tools/package.json", "utf8"));
const lock = JSON.parse(await readFile("tools/scripts/kotlin-toolchain.lock.json", "utf8"));
const gradle = await readFile("build.gradle.kts", "utf8");
if (!gradle.includes(`version = "${version}"`) || !gradle.includes(`kotlin("jvm") version "${lock.kotlin}"`)) {
  throw new Error("Keep tools/package.json, build.gradle.kts and the Kotlin toolchain lock versions aligned.");
}
if (process.env.GITHUB_REF?.startsWith("refs/tags/") && process.env.GITHUB_REF !== `refs/tags/v${version}`) {
  throw new Error(`The release tag must match the package version: v${version}`);
}
const artifacts = join(root, "artifacts");
await mkdir(artifacts, {recursive: true});
const work = await mkdtemp(join(artifacts, "release-build-"));
const cache = join(artifacts, "toolchain-cache");
await mkdir(cache, {recursive: true});
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "java.exe" : "java") : "java";
const info = spawnSync(java, ["-version"], {encoding: "utf8"});
const javaVersion = /version "(\d+)/.exec(info.stderr ?? "")?.[1];
if (info.error || !javaVersion || Number(javaVersion) < 17) throw new Error("Set JAVA_HOME to JDK 17 or newer before building a release.");

const hash = bytes => createHash("sha256").update(bytes).digest("hex");
const jars = new Map();
for (const entry of lock.artifacts) jars.set(entry.name, await dependency(entry));

/** Every compiler/runtime input is pinned by content, including locally cached Gradle artifacts. */
async function dependency(entry) {
  const filename = `${entry.name}-${entry.version}.jar`;
  const target = join(cache, filename);
  const valid = async file => {
    try { return hash(await readFile(file)) === entry.sha256; } catch { return false; }
  };
  if (await valid(target)) return target;
  const gradle = process.env.GRADLE_USER_HOME ?? join(homedir(), ".gradle");
  const candidates = join(gradle, "caches/modules-2/files-2.1", entry.group, entry.name, entry.version);
  try {
    for (const folder of await readdir(candidates)) {
      const candidate = join(candidates, folder, filename);
      if (await valid(candidate)) { await copyFile(candidate, target); return target; }
    }
  } catch { /* A clean machine downloads the same pinned bytes below. */ }
  const url = `https://repo.maven.apache.org/maven2/${entry.group.replaceAll(".", "/")}/${entry.name}/${entry.version}/${filename}`;
  const response = await fetch(url, {signal: AbortSignal.timeout(180000)});
  if (!response.ok) throw new Error(`Download failed (${response.status}): ${url}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  if (hash(bytes) !== entry.sha256) throw new Error(`Checksum mismatch: ${filename}`);
  await writeFile(target, bytes);
  return target;
}

async function files(directory) {
  const output = [];
  for (const entry of await readdir(directory, {withFileTypes: true})) {
    if (["build", ".gradle", ".kotlin", ".idea", "local.properties"].includes(entry.name)) continue;
    const file = join(directory, entry.name);
    if (entry.isDirectory()) output.push(...await files(file));
    else output.push(file);
  }
  return output.sort();
}

function run(args) {
  const result = spawnSync(java, args, {stdio: "inherit"});
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`Java task failed (exit ${result.status})`);
}

const compilerPath = [...jars.values()].join(delimiter);
const stdlib = jars.get("kotlin-stdlib");
const annotations = jars.get("annotations");
const zxing = jars.get("core");
async function compile(directories, destination, classpath, module) {
  await mkdir(destination, {recursive: true});
  const sources = (await Promise.all(directories.map(files))).flat().filter(file => file.endsWith(".kt"));
  run(["-cp", compilerPath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "11",
    "-module-name", module, "-classpath", classpath.join(delimiter), "-d", destination, ...sources]);
}

const coreClasses = join(work, "core");
const driverClasses = join(work, "escpos");
const testClasses = join(work, "tests");
await compile(["core/src/main/kotlin"], coreClasses, [stdlib, annotations], "receiptscript_core");
await compile(["drivers/escpos/src/main/kotlin"], driverClasses, [coreClasses, stdlib, annotations, zxing], "receiptscript_escpos");
const testPath = [coreClasses, driverClasses, stdlib, annotations, zxing, jars.get("junit"), jars.get("hamcrest-core")];
await compile(["core/src/test/kotlin", "drivers/escpos/src/test/kotlin", "examples/kotlin", "examples/kotlin-drivers"],
  testClasses, testPath, "receiptscript_tests");
const testNames = [];
for (const file of [...await files("core/src/test/kotlin"), ...await files("drivers/escpos/src/test/kotlin")].filter(file => file.endsWith("Test.kt"))) {
  const source = await readFile(file, "utf8");
  const packageName = /^package\s+(\S+)/m.exec(source)[1];
  const name = /([^/\\]+)\.kt$/.exec(file)[1];
  testNames.push(`${packageName}.${name}`);
}
run(["-cp", [testClasses, ...testPath].join(delimiter), "org.junit.runner.JUnitCore", ...testNames]);

const stage = join(work, `receiptscript-${version}`);
await mkdir(join(stage, "lib"), {recursive: true});
await mkdir(join(stage, "optional"), {recursive: true});
await mkdir(join(stage, "runtime-jvm"), {recursive: true});
await mkdir(join(stage, "sources"), {recursive: true});
const coreJar = join(stage, "lib", `receiptscript-core-${version}.jar`);
const driverJar = join(stage, "lib", `receiptscript-escpos-${version}.jar`);
const mtime = new Date("2020-01-01T00:00:00Z");
async function zip(destination, entries) {
  await mkdir(dirname(destination), {recursive: true});
  const archive = new yazl.ZipFile();
  const output = createWriteStream(destination);
  const done = new Promise((resolve, reject) => {
    output.on("close", resolve).on("error", reject);
    archive.outputStream.on("error", reject).pipe(output);
  });
  for (const entry of entries.sort((a, b) => a.name.localeCompare(b.name, "en"))) {
    const name = entry.name.replaceAll("\\", "/");
    archive.addBuffer(entry.bytes, name, {mtime, mode: name.endsWith("/gradlew") || name.endsWith(".sh") ? 0o100755 : 0o100644});
  }
  archive.end();
  await done;
}
async function jar(directory, target, prefix = "") {
  const entries = await Promise.all((await files(directory)).map(async file => ({name: prefix + file.slice(directory.length + 1), bytes: await readFile(file)})));
  entries.push({name: "META-INF/MANIFEST.MF", bytes: Buffer.from(`Manifest-Version: 1.0\r\nImplementation-Title: ReceiptScript\r\nImplementation-Version: ${version}\r\n\r\n`)});
  entries.push({name: "META-INF/LICENSE", bytes: await readFile("LICENSE")});
  await zip(target, entries);
}
await jar(coreClasses, coreJar);
await jar(driverClasses, driverJar);
await jar("core/src/main/kotlin", join(stage, "sources", `receiptscript-core-${version}-sources.jar`));
await jar("drivers/escpos/src/main/kotlin", join(stage, "sources", `receiptscript-escpos-${version}-sources.jar`));
await copyFile(zxing, join(stage, "optional", "zxing-core-3.5.3.jar"));
await copyFile(stdlib, join(stage, "runtime-jvm", `kotlin-stdlib-${lock.kotlin}.jar`));

// Prove the published surface works using only the assembled JARs and their runtime dependencies.
const consumer = join(work, "consumer");
await compile(["tools/test/release", "examples/readme"], consumer, [coreJar, driverJar, stdlib, zxing], "release_consumer");
run(["-cp", [consumer, coreJar, driverJar, stdlib, zxing].join(delimiter), "receiptscript.releasecheck.ConsumerKt", resolve("examples/readme/sale.rct")]);

for (const name of ["LICENSE", "THIRD_PARTY_NOTICES.md"]) await copyFile(name, join(stage, name));
await mkdir(join(stage, "licenses"), {recursive: true});
await copyFile("docs/licenses/Apache-2.0.txt", join(stage, "licenses", "Apache-2.0.txt"));
await writeFile(join(stage, "README.md"), packagedReadme);
await mkdir(join(stage, "examples"), {recursive: true});
for (const file of await files("examples")) {
  const target = join(stage, file);
  await mkdir(dirname(target), {recursive: true});
  await copyFile(file, target);
}
// The Android starter's optional renderer is built from source in the binary distribution.
for (const file of await files("drivers/android")) {
  const target = join(stage, file);
  await mkdir(dirname(target), {recursive: true});
  await copyFile(file, target);
}
await writeFile(join(stage, "release.json"), JSON.stringify({version, kotlin: lock.kotlin, jvmTarget: 11,
  runtimeDependencies: [{name: "kotlin-stdlib", version: lock.kotlin}, {name: "zxing-core", version: "3.5.3", optional: true}],
  verification: ["core and driver JUnit tests", "consumer compiled and executed against packaged JARs"]}, null, 2) + "\n");
const checksums = [];
for (const file of await files(stage)) checksums.push(`${hash(await readFile(file))}  ${file.slice(stage.length + 1).replaceAll("\\", "/")}`);
await writeFile(join(stage, "SHA256SUMS"), checksums.join("\n") + "\n");
const archivePath = join(artifacts, `receiptscript-${version}-binary.zip`);
await zip(archivePath, await Promise.all((await files(stage)).map(async file => ({name: `receiptscript-${version}/${file.slice(stage.length + 1)}`, bytes: await readFile(file)}))));
for (const file of [coreJar, driverJar]) await copyFile(file, join(artifacts, file.split(/[\\/]/).at(-1)));
await writeFile(`${archivePath}.sha256`, `${hash(await readFile(archivePath))}  receiptscript-${version}-binary.zip\n`);
console.log(`Verified binary release: ${archivePath} (${(await stat(archivePath)).size} bytes)`);
