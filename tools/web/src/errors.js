/**
 * Parse error with a one-based line number where available.
 */
export class RctParseError extends Error {
  constructor(message, line = null) {
    super(message);
    this.name = "RctParseError";
    this.line = line ?? lineFromMessage(message);
  }
}

const LINE_IN_MESSAGE = /[Ll]ine (\d+)/;

function lineFromMessage(message) {
  const match = LINE_IN_MESSAGE.exec(message ?? "");
  return match ? Number(match[1]) : null;
}
