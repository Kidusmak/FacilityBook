# Diagrams

Source files and rendered images for the project diagrams.

| Diagram | Source | Rendered |
| --- | --- | --- |
| Entity-relationship diagram | [`erd.mmd`](erd.mmd) | [`erd.png`](erd.png) |
| System architecture | [`architecture.mmd`](architecture.mmd) | [`architecture.png`](architecture.png) |
| Use cases | [`use-cases.mmd`](use-cases.mmd) | [`use-cases.png`](use-cases.png) |

The same Mermaid sources are embedded inline in [`../ERD.md`](../ERD.md),
[`../ARCHITECTURE.md`](../ARCHITECTURE.md), and [`../USE_CASES.md`](../USE_CASES.md), so
they render directly on GitHub.

## Regenerating the images

With Node.js and Google Chrome installed:

```bash
# Windows (PowerShell)
$env:PUPPETEER_EXECUTABLE_PATH='C:\Users\<you>\AppData\Local\Google\Chrome\Application\chrome.exe'
npx -y @mermaid-js/mermaid-cli -i erd.mmd -o erd.png -b white -s 2
npx -y @mermaid-js/mermaid-cli -i architecture.mmd -o architecture.png -b white -s 2
npx -y @mermaid-js/mermaid-cli -i use-cases.mmd -o use-cases.png -b white -s 2
```

```bash
# macOS / Linux
npx -y @mermaid-js/mermaid-cli -i erd.mmd -o erd.png -b white -s 2
```

Alternatively, paste any `.mmd` file into <https://mermaid.live> and export a PNG/SVG.
