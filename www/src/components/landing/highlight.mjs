/**
 * A small build-time Java highlighter for the landing page's code samples.
 * The docs pages use Starlight's highlighter; this keeps the landing page free of
 * client-side code and of a second highlighting dependency.
 */
const KEYWORDS = new Set(['new', 'return', 'import', 'public', 'class', 'static', 'void', 'final']);

const TOKEN = /("(?:[^"\\]|\\.)*")|(\/\/.*)|\b([A-Z][A-Za-z0-9]*)\b|\.?\b([a-z][A-Za-z0-9]*)(?=\()|\b([a-z]+)\b/g;

const escape = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

export function highlightJava(code) {
  let out = '';
  let last = 0;
  for (const m of code.matchAll(TOKEN)) {
    out += escape(code.slice(last, m.index));
    last = m.index + m[0].length;
    const [text, str, comment, type, method, word] = m;
    if (str) out += `<span class="t-str">${escape(text)}</span>`;
    else if (comment) out += `<span class="t-com">${escape(text)}</span>`;
    else if (type) out += `<span class="t-type">${text}</span>`;
    else if (method) {
      const dot = text.startsWith('.') ? '.' : '';
      out += `${dot}<span class="t-fn">${method}</span>`;
    } else if (word && KEYWORDS.has(word)) out += `<span class="t-kw">${word}</span>`;
    else out += escape(text);
  }
  return out + escape(code.slice(last));
}
