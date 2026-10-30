const fs = require("node:fs");
const path = require("node:path");
const { ReceiptScript, lint } = require("./dist/core.cjs");
const { HtmlRenderer } = require("./dist/html.cjs");

/** Fragment and image references are confined to the open template's directory. */
function localPath(directory, reference) {
  if (path.isAbsolute(reference)) throw new Error("References must be relative to the template");
  const resolved = path.resolve(directory, reference);
  const relative = path.relative(directory, resolved);
  if (relative.startsWith("..") || path.isAbsolute(relative)) throw new Error("Reference escapes the template directory");
  const actual = fs.realpathSync(resolved);
  const actualRelative = path.relative(fs.realpathSync(directory), actual);
  if (actualRelative.startsWith("..") || path.isAbsolute(actualRelative)) throw new Error("Reference escapes the template directory");
  return actual;
}

function preview(source, data, filename) {
  const directory = path.dirname(filename);
  const engine = new ReceiptScript({loadTemplate(id) {
    return fs.readFileSync(localPath(directory, `${id}.rct`), "utf8");
  }});
  const receipt = engine.compile(source).render(data);
  return new HtmlRenderer({resolveImage(reference) {
    const file = localPath(directory, reference.replace(/^asset:/, ""));
    const mime = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".gif": "image/gif", ".webp": "image/webp"}[path.extname(file).toLowerCase()];
    if (!mime) throw new Error("Preview images must be PNG, JPEG, GIF or WebP");
    if (fs.statSync(file).size > 5 * 1024 * 1024) throw new Error("Preview images must be smaller than 5 MiB");
    return `data:${mime};base64,${fs.readFileSync(file).toString("base64")}`;
  }}).render(receipt).replace("<head>", '<head><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; img-src data:; style-src \'unsafe-inline\';">');
}

function activate(context) {
  const vscode = require("vscode");
  const spec = require("./rct-language-spec.json");
  const diagnostics = vscode.languages.createDiagnosticCollection("receiptscript");
  let panel, activeUri, timer;
  function updatePreview() {
    if (!panel || !activeUri) return;
    const document = vscode.workspace.textDocuments.find(doc => doc.uri.toString() === activeUri);
    if (!document) return;
    try {
      const filename = document.uri.fsPath;
      const sample = filename.replace(/\.rct$/i, ".preview.json");
      if (!fs.existsSync(sample)) throw new Error(`Create ${path.basename(sample)} beside the template to supply preview data.`);
      const sampleDoc = vscode.workspace.textDocuments.find(doc => doc.uri.fsPath === sample);
      panel.webview.html = preview(document.getText(), JSON.parse(sampleDoc?.getText() ?? fs.readFileSync(sample, "utf8")), filename);
    } catch (error) {
      const escape = value => String(value).replace(/[&<>"']/g, c => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"}[c]));
      panel.webview.html = `<html><body style="font-family:sans-serif;padding:24px"><h2>Receipt preview</h2><p>${escape(error.message)}</p></body></html>`;
    }
  }
  function refresh(document) {
    if (document.languageId !== "rct") return;
    const first = document.getText().split(/\r?\n/).find(line => line.trim() && !line.trim().startsWith("#"));
    const issues = lint(document.getText(), {fragment: !/^receipt\s/.test(first?.trim() ?? "")});
    diagnostics.set(document.uri, issues.map(issue => new vscode.Diagnostic(
      new vscode.Range(Math.max(0, (issue.line ?? 1) - 1), Math.max(0, issue.column - 1), Math.max(0, (issue.line ?? 1) - 1), issue.column),
      issue.message, issue.severity === "error" ? vscode.DiagnosticSeverity.Error : vscode.DiagnosticSeverity.Warning)));
    clearTimeout(timer);
    timer = setTimeout(updatePreview, 150);
  }
  const watcher = vscode.workspace.createFileSystemWatcher("**/*.{rct,json,png,jpg,jpeg,webp,gif}");
  context.subscriptions.push(diagnostics, watcher, watcher.onDidChange(updatePreview), watcher.onDidCreate(updatePreview), watcher.onDidDelete(updatePreview),
    vscode.workspace.onDidOpenTextDocument(refresh),
    vscode.workspace.onDidChangeTextDocument(event => {
      if (event.document.languageId === "rct") refresh(event.document);
      else if (event.document.uri.fsPath.endsWith(".preview.json")) updatePreview();
    }),
    vscode.workspace.onDidCloseTextDocument(doc => diagnostics.delete(doc.uri)),
    {dispose() {clearTimeout(timer); panel?.dispose();}},
    vscode.languages.registerCompletionItemProvider("rct", {provideCompletionItems() {
      return [...spec.statements, ...spec.filters].map(entry => {
        const item = new vscode.CompletionItem(entry.name, vscode.CompletionItemKind.Keyword);
        item.documentation = entry.description;
        item.insertText = new vscode.SnippetString(entry.snippet ?? entry.name);
        return item;
      });
    }}, "{", "|"),
    vscode.languages.registerHoverProvider("rct", {provideHover(document, position) {
      const range = document.getWordRangeAtPosition(position);
      const entry = [...spec.statements, ...spec.filters].find(item => item.name === (range && document.getText(range)));
      return entry ? new vscode.Hover(entry.description) : null;
    }}),
    vscode.commands.registerCommand("rct.openPreview", () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor || editor.document.languageId !== "rct") return vscode.window.showInformationMessage("Open an .rct file first.");
      if (!vscode.workspace.isTrusted) return vscode.window.showWarningMessage("Trust this workspace to load local receipt preview data and images.");
      activeUri = editor.document.uri.toString();
      if (!panel) {
        panel = vscode.window.createWebviewPanel("rctPreview", "ReceiptScript Preview", vscode.ViewColumn.Beside, {enableScripts: false, localResourceRoots: []});
        panel.onDidDispose(() => {panel = undefined;}, null, context.subscriptions);
      } else panel.reveal(vscode.ViewColumn.Beside);
      updatePreview();
    }));
  vscode.workspace.textDocuments.forEach(refresh);
}
module.exports = {activate, preview};
