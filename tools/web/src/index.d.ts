export type Alignment = "left" | "center" | "right";
export type ColumnAlignment = "start" | "center" | "end";
export type FontSize = "small" | "middle" | "big";
export type BarcodeFormat = "code128" | "code39" | "ean13" | "ean8" | "upca" | "itf";
export interface Meta {
  width?: number | null; paper?: string | null; copy?: string | null; templateVersion?: number | null;
  font?: number | null; fontScaleMode?: "relative" | "fixed" | null; extra?: Record<string, string>;
}
export interface Style { size: FontSize; fontSize: number | null; bold: boolean; underline: boolean }
export interface TextNode extends Style { type: "text"; text: string; align: Alignment }
export interface KeyValueNode extends Style { type: "kv"; key: string; value: string }
export interface DividerNode { type: "divider"; pattern: string; length: number | null }
export interface SpacerNode { type: "spacer"; lines: number }
export interface RowNode { type: "row"; columns: string[] }
export interface TableNode { type: "table"; headers: string[]; weights: number[] | null; alignments: ColumnAlignment[] | null; children: Node[] }
export interface ImageNode { type: "image"; source: string; width: number | null; height: number | null }
export interface QrNode { type: "qr"; content: string; size: number }
export interface BarcodeNode { type: "barcode"; content: string; width: number; height: number; format: BarcodeFormat }
export interface IfNode { type: "if"; path: string; children: Node[] }
export interface ForNode { type: "for"; item: string; collection: string; children: Node[] }
export type Node = TextNode | KeyValueNode | DividerNode | SpacerNode | RowNode | TableNode | ImageNode | QrNode | BarcodeNode | IfNode | ForNode;
export interface Document {name: string; meta: Meta | null; body: Node[]; header: Node[]; footer: Node[]; headerRefs: string[]; footerRefs: string[]}
export interface Origin {path: (string | number)[]; shared?: {section: string; id: string; index: number}}
export interface TableBlock {type: "table"; headers: string[]; rows: string[][]; weights: number[] | null; alignments: ColumnAlignment[] | null; lineWidth: number; headerSize: FontSize; headerFontSize: number | null; headerBold: boolean; rowSize: FontSize; rowFontSize: number | null}
export type Block = (TextNode & {role?: "divider" | "row"} | KeyValueNode | {type: "spacer"} | TableBlock | ImageNode | QrNode | BarcodeNode) & {origin: Origin | null};
export interface ReceiptAdapter<T> {render(receipt: Receipt): T}
export interface ReceiptScriptOptions {templates?: Record<string, string>; loadTemplate?: (id: string) => string | null | undefined; missingBindings?: "error" | "empty"}
export class ReceiptScript {
  constructor(options?: ReceiptScriptOptions);
  compile(source: string): CompiledTemplate;
  template(id: string): CompiledTemplate;
}
export class CompiledTemplate {readonly document: Document; render(data: unknown): Receipt}
export class Receipt {constructor(name: string, meta: Meta | null, blocks: Block[]); readonly name: string; readonly meta: Meta | null; readonly blocks: Block[]; render<T>(adapter: ReceiptAdapter<T>): T}
export class RctParseError extends Error {line: number | null}
export interface Context {root: unknown; locals: Record<string, unknown>; missingBindings?: "error" | "empty"}
export interface Issue {severity: "error" | "warning"; code: string; line: number | null; column: number; message: string; params: Record<string, unknown>; node?: Node}
export function parse(source: string): Document;
export function parseFragment(source: string): Node[];
export function write(document: Document): string;
export function writeFragment(nodes: Node[]): string;
export function writeNodes(nodes: Node[], depth: number): string[];
export function createContext(root: unknown, locals?: Record<string, unknown>, missingBindings?: "error" | "empty"): Context;
export function withLocals(context: Context, locals: Record<string, unknown>): Context;
export function layout(document: Document, context: Context): Block[];
export function composeFragments(document: Document, loadFragment?: (id: string) => string | null | undefined): {document: Document; missing: {section: string; id: string}[]};
export function lint(source: string, options?: {fragment?: boolean}): Issue[];
export function inspectNodes(nodes: Node[], options?: {inTable?: boolean}): Issue[];
export function templatesOf(node: Node): string[];
export function isValidPath(path: string): boolean;
export function render(raw: string, context: Context): string;
export function resolveValue(path: string, context: Context): unknown;
export function isTruthy(path: string, context: Context): boolean;
export function truthy(value: unknown): boolean;
export function stringify(value: unknown): string;
export interface BindingPart {kind: "binding"; path: string; filters: {name: string; args: string[]}[]}
export type TemplatePart = {kind: "literal"; text: string} | BindingPart;
export function parseTemplateString(raw: string): TemplatePart[];
export function parseBinding(raw: string): BindingPart;
export function bindingsOf(raw: string): BindingPart[];
export function formatBinding(path: string, filters?: {name: string; args: string[]}[]): string;
export const FILTERS: readonly string[];
export const BARCODE_FORMATS: readonly BarcodeFormat[];
export const DEFAULT_WIDTH: number;
export const FOOTER_GAP_LINES: number;
export const RELATIVE_FONT_STEP: number;
export const DEFAULT_QR_SIZE: number;
export const DEFAULT_BARCODE_WIDTH: number;
export const DEFAULT_BARCODE_HEIGHT: number;
export function fontPolicyOf(meta: Meta | null): {base: number | null; mode: string};
export function resolveFontSize(policy: ReturnType<typeof fontPolicyOf>, style: FontSize, inlineSize: number | null): number | null;
export function loopContexts(node: ForNode, context: Context): Context[];
export function tableRows(nodes: Node[], context: Context): string[][];
export function dividerText(node: DividerNode, width: number): string;
export function repeatPattern(pattern: string, targetLength: number): string;
export function rowText(values: string[], width: number): string;
export function renderNodes(nodes: Node[], context: Context, environment: {width: number; policy: ReturnType<typeof fontPolicyOf>}, path: (string | number)[]): Block[];
export const STYLE_FONT_PX: Readonly<Record<FontSize, number>>;
export const MONOSPACE_ADVANCE_EM: number;
export const LINE_HEIGHT: number;
export const PAPER_DOTS: Readonly<Record<string, number>>;
export const DEFAULT_PAPER: string;
export function paperDots(paper: string | null | undefined): number;
export interface Geometry {width: number; dots: number; middlePx: number; lineWidthPx: number; lineHeightPx: number; dotScale: number; fontPx(style: FontSize, inline?: number | null): number}
export function geometry(meta: Meta | null): Geometry;
export function blockFontPx(block: Block, geometryInfo: Geometry, sizeKey?: string, fontKey?: string): number;
export function columnFractions(weights: number[] | null | undefined, count: number): number[];
export function fitImage(natural: {width: number; height: number}, node: ImageNode, geometryInfo: Geometry): {width: number; height: number};
