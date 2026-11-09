import { chromium } from "playwright";
import { writeFileSync, readFileSync } from "node:fs";
const [page_, out, shot] = process.argv.slice(2);
const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH });
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
await page.goto("file://" + page_, { waitUntil: "networkidle" });
await page.waitForTimeout(1500);
await page.screenshot({ path: shot });
const band = await page.evaluate(() => {
  const num = (s) => s.match(/-?[\d.]+(e-?\d+)?/g).map(Number);
  const nodes = [...document.querySelectorAll("#world .node")].map((g) => {
    const [x, y] = num(g.getAttribute("transform"));
    const state = ["done", "available", "locked"].find((c) => g.classList.contains(c));
    return { k: g.dataset.key, x, y, state, caption: g.querySelector(".caption")?.textContent ?? "" };
  });
  const edges = [...document.querySelectorAll("#world .edge")].map((p) => ({
    from: p.dataset.from, to: p.dataset.to, done: p.classList.contains("done"), d: p.getAttribute("d") }));
  const junctions = [...document.querySelectorAll("#world .junction")].map((c) => ({
    x: +c.getAttribute("cx"), y: +c.getAttribute("cy"), done: c.classList.contains("done") }));
  const cols = [...document.querySelectorAll("#world rect.col")].map((r, i) => ({
    x: +r.getAttribute("x"), y: +r.getAttribute("y"), w: +r.getAttribute("width"), h: +r.getAttribute("height"),
    current: r.classList.contains("current"), label: document.querySelectorAll("#world .col-label")[i].textContent }));
  return { nodes, edges, junctions, cols };
});
writeFileSync(out, JSON.stringify(band, null, 1));
await browser.close();
console.log(band.nodes.length, "nodes", band.edges.length, "edges", band.cols.length, "cols");
