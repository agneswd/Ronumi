// Builds ronumi.agne.uk into site/dist: copies site/public and renders docs/privacy.md into /privacy/.
// Run from the repository root: node site/build.mjs
// The privacy policy has one source, docs/privacy.md, so the app link and the site never differ.
import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";

const dist = "site/dist";
rmSync(dist, { recursive: true, force: true });
cpSync("site/public", dist, { recursive: true });

const escape = (text) => text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

// Enough Markdown for our docs: headings, paragraphs, lists, links, bold, and inline code.
function inline(text) {
  return escape(text)
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, '<a href="$2">$1</a>');
}

function markdown(source) {
  const html = [];
  let paragraph = [];
  let list = [];
  const flush = () => {
    if (paragraph.length) html.push(`<p>${inline(paragraph.join(" "))}</p>`);
    if (list.length) html.push(`<ul>${list.map((item) => `<li>${inline(item)}</li>`).join("")}</ul>`);
    paragraph = [];
    list = [];
  };
  for (const raw of source.split("\n")) {
    const line = raw.trim();
    const heading = line.match(/^(#{1,3}) (.+)$/);
    if (!line) flush();
    else if (heading) {
      flush();
      html.push(`<h${heading[1].length}>${inline(heading[2])}</h${heading[1].length}>`);
    } else if (/^[-*] /.test(line)) {
      if (paragraph.length) flush();
      list.push(line.slice(2));
    } else paragraph.push(line);
  }
  flush();
  return html.join("\n");
}

const page = (title, body) => `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title}</title>
  <meta name="theme-color" content="#5362d6">
  <link rel="icon" href="/img/icon.svg" type="image/svg+xml">
  <link rel="stylesheet" href="/styles.css">
</head>
<body>
  <header class="wrap top">
    <a class="brand" href="/"><img src="/img/icon.svg" alt="">Ronumi</a>
    <nav><a href="/#features">Features</a><a href="/#support">Support</a></nav>
  </header>
  <main class="doc">
${body}
  </main>
  <footer>
    <div class="wrap">
      <span>Ronumi by <a href="https://agne.uk">Agne</a></span>
      <nav><a href="https://github.com/agneswd/Ronumi">Source code</a></nav>
    </div>
  </footer>
</body>
</html>
`;

mkdirSync(`${dist}/privacy`, { recursive: true });
writeFileSync(`${dist}/privacy/index.html`, page("Ronumi privacy policy", markdown(readFileSync("docs/privacy.md", "utf8"))));
console.log("Built", dist);
