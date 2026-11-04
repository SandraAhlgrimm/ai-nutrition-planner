import { chromium } from "@playwright/test";
import { mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const artifacts = resolve(process.argv[2] || resolve(here, "../test-results/visual"));
const tracks = ["comparison", "langchain4j", "spring-ai", "embabel"];
const projects = ["light-720", "dark-720", "light-1080", "dark-1080"];
const browser = await chromium.launch();
try {
  const page = await browser.newPage();
  await page.goto(pathToFileURL(resolve(here, "../index.html")).href);
  let count = 0;
  for (const project of projects) {
    const destination = resolve(artifacts, "contacts", project);
    await mkdir(destination, { recursive: true });
    for (const track of tracks) {
      const directory = resolve(artifacts, project, track);
      const images = (await readdir(directory)).filter(file => /^\d+-[\w-]+\.png$/.test(file)).sort();
      for (let start = 0; start < images.length; start += 4) {
        const batch = await Promise.all(images.slice(start, start + 4).map(async file => ({
          name: `${track} / ${file.replace(".png", "")}`,
          data: (await readFile(resolve(directory, file))).toString("base64")
        })));
        const data = await page.evaluate(async ({ batch, dark }) => {
          document.documentElement.dataset.theme = dark ? "dark" : "light";
          const colors = getComputedStyle(document.documentElement);
          const canvas = document.createElement("canvas");
          canvas.width = 1328;
          canvas.height = 840;
          const context = canvas.getContext("2d");
          context.fillStyle = colors.getPropertyValue("--cp-bg").trim();
          context.fillRect(0, 0, canvas.width, canvas.height);
          context.fillStyle = colors.getPropertyValue("--cp-text").trim();
          context.font = '18px "Segoe UI", Aptos, Calibri, sans-serif';
          for (const [i, item] of batch.entries()) {
            const image = new Image();
            image.src = `data:image/png;base64,${item.data}`;
            await image.decode();
            const x = 16 + (i % 2) * 656;
            const y = 16 + Math.floor(i / 2) * 408;
            context.fillText(item.name, x, y + 20, 640);
            context.drawImage(image, x, y + 32, 640, 360);
          }
          return canvas.toDataURL("image/png").split(",")[1];
        }, { batch, dark: project.startsWith("dark") });
        await writeFile(resolve(destination, `${track}-${String(start + 1).padStart(2, "0")}-${String(Math.min(start + 4, images.length)).padStart(2, "0")}.png`), Buffer.from(data, "base64"));
        count++;
      }
    }
  }
  console.log(`Created ${count} four-slide contact sheets in ${resolve(artifacts, "contacts")}`);
} finally {
  await browser.close();
}
