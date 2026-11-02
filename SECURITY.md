# Security policy

ReceiptScript prints payment and customer data, so security reports are taken seriously.

## Supported versions

Until 1.0, only the latest release gets security fixes.

## Reporting a vulnerability

**Do not open a public issue.** Report it privately through GitHub:
**Security → Report a vulnerability** on the [repository page](https://github.com/DonnC/receiptscript/security/advisories/new).

Please include:

- what an attacker can do, and under which conditions;
- a minimal template, data or byte sequence that reproduces it, using fictional data only;
- the affected version.

You will get an acknowledgement within 7 days. Once a fix is ready, it will be released and the
advisory published, crediting you unless you prefer otherwise.

## Scope

Examples of issues in scope:

- template data that injects printer control commands into ESC/POS output;
- template or image references that read files outside the intended folder;
- sensitive values that escape masking filters;
- crashes or unbounded memory use caused by template content.

Out of scope: vulnerabilities in printer firmware, vendor SDKs, or how an app stores receipts it exports.

## Handling sensitive data

ReceiptScript does not store or transmit receipts. Your app decides what goes into a template. Never
print PINs, CVVs, passwords or card track data, and mask card numbers with `maskPan`.
