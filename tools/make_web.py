#!/usr/bin/env python3
"""Build the web version of the library (the Claude artifact) from the APK's own screens.

Usage: python3 tools/make_web.py <output.html>

Same index.html, so the app and the web library always look identical. Differences:
- fonts come from Google Fonts instead of the APK's bundled files
- the page reads lessons live from the artifact database (tools/web_source.js),
  falling back to the current content.json when no database is reachable
- light/dark also follow the viewer's explicit theme choice
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "app", "src", "main", "assets")


def main(out):
    s = open(os.path.join(A, "index.html"), encoding="utf-8").read()
    content = open(os.path.join(A, "content.json"), encoding="utf-8").read()
    src = open(os.path.join(ROOT, "tools", "web_source.js"), encoding="utf-8").read()
    src = src.replace("/*MZ:FALLBACK*/null", content.replace("</", "<\\/"))

    # page content only: the artifact host adds the document skeleton
    s = re.sub(r"<!doctype html>\s*", "", s, flags=re.I)
    s = re.sub(r"</?html[^>]*>\s*", "", s)
    s = re.sub(r"</?head>\s*", "", s)
    s = re.sub(r"</?body>\s*", "", s)
    s = re.sub(r'<meta charset="utf-8">\s*', "", s)
    s = re.sub(r'<meta name="viewport"[^>]*>\s*', "", s)
    s = s.replace("<!--MZ:FONTS-->",
                  '<meta name="claude:viewport-fit-sides" content="cover">\n'
                  '<link rel="preconnect" href="https://fonts.googleapis.com">\n'
                  '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Vazirmatn:wght@400;500;700;800&display=swap">')
    s = re.sub(r'@font-face\{font-family:"Vaz";src:url\("fonts/[^}]*\}\n', "", s)

    # honour the viewer's explicit light/dark choice as well as the system setting
    m = re.search(r"@media \(prefers-color-scheme:dark\)\{:root\{(.*?)\}\}", s, re.S)
    if not m:
        sys.exit("dark token block not found")
    dark = m.group(1)
    s = s.replace(m.group(0),
                  '@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){' + dark + "}}\n"
                  ':root[data-theme="dark"]{' + dark + "}")
    s = s.replace("@media (prefers-color-scheme:dark){.glass{",
                  '@media (prefers-color-scheme:dark){:root:not([data-theme="light"]) .glass{')

    s = s.replace("<!--MZ:SOURCE-->", "<script>\n" + src + "\n</script>")
    open(out, "w", encoding="utf-8").write(s)
    print("web library written:", out, len(s) // 1024, "KB")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
