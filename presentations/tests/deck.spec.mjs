import { test, expect } from "@playwright/test";
import { readFile, mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "../..");
const presentation = pathToFileURL(resolve(here, "../index.html")).href;
const artifacts = resolve(process.env.QA_DIR || resolve(here, "../test-results/visual"));
const trackIds = ["comparison", "langchain4j", "spring-ai", "embabel"];
const normalize = text => text.trim().split("\n").map(line => line.trim()).join("\n");
let errors;
let networkRequests;

test.beforeEach(async ({ page, context }) => {
  errors = [];
  networkRequests = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  page.on("request", request => { if (/^https?:/.test(request.url())) networkRequests.push(request.url()); });
  await context.setOffline(true);
  await page.goto(presentation);
});

test.afterEach(async () => {
  expect.soft(errors, "No runtime or console errors").toEqual([]);
  expect.soft(networkRequests, "The presentation must not request remote assets").toEqual([]);
});

async function navigate(page, track, slug) {
  await page.goto(`${presentation}#/${track}/${slug}`);
  await expect(page.locator("#stage .slide:not([hidden])")).toHaveAttribute("data-slide-id", slug);
}

function layoutProblems(slide) {
  const problems = [];
  const area = slide.querySelector(".slide-body").getBoundingClientRect();
  const tolerance = 1.1;
  const visible = element => element.getClientRects().length > 0;
  const label = element => `${element.tagName.toLowerCase()}.${element.className}: ${element.textContent.trim().slice(0, 65)}`;
  const inside = (inner, outer) => inner.left >= outer.left - tolerance && inner.right <= outer.right + tolerance
    && inner.top >= outer.top - tolerance && inner.bottom <= outer.bottom + tolerance;
  for (const element of slide.querySelector(".slide-body").children) {
    if (visible(element) && !inside(element.getBoundingClientRect(), area)) problems.push(`Outside body: ${label(element)}`);
  }
  for (const selector of [".slide-head", ".slide-body", ".slide-footer", ".card", ".node", ".code-panel", ".stop", ".demo-panel", ".outcome", ".source-list a"]) {
    for (const element of slide.querySelectorAll(selector)) {
      if (!visible(element)) continue;
      if (element.scrollWidth > element.clientWidth + 1 || element.scrollHeight > element.clientHeight + 1) {
        problems.push(`Scroll overflow: ${label(element)}`);
      }
      const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
      const bounds = element.getBoundingClientRect();
      for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        if (!node.textContent.trim() || !visible(node.parentElement)) continue;
        const range = document.createRange();
        range.selectNodeContents(node);
        for (const rect of range.getClientRects()) {
          if (rect.width > 0 && rect.height > 0 && !inside(rect, bounds)) problems.push(`Text escapes ${label(element)}: ${node.textContent.trim().slice(0, 35)}`);
        }
      }
    }
  }
  for (const selector of [".cards", ".split", ".flow", ".budget", ".route", ".lane", ".hero-grid", ".closing", ".source-list", ".code-label", ".demo-steps"]) {
    for (const group of slide.querySelectorAll(selector)) {
      const children = [...group.children].filter(visible);
      for (let i = 0; i < children.length; i++) {
        for (let j = i + 1; j < children.length; j++) {
          const a = children[i].getBoundingClientRect();
          const b = children[j].getBoundingClientRect();
          if (Math.min(a.right, b.right) - Math.max(a.left, b.left) > tolerance
            && Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top) > tolerance) {
            problems.push(`Overlapping siblings: ${label(children[i])} / ${label(children[j])}`);
          }
        }
      }
    }
  }
  return [...new Set(problems)];
}

test("four standalone talks have unique routes and exactly 45 timed minutes", async ({ page }) => {
  const manifest = await page.evaluate(() => window.presentationManifest);
  expect(manifest.map(track => track.id)).toEqual(trackIds);
  await expect(page.locator("#track-menu a")).toHaveCount(4);
  for (const track of manifest) {
    expect(track.mainCount).toBe(24);
    expect(track.minutes).toBe(45);
    expect(track.slides.filter(slide => !slide.backup).reduce((sum, slide) => sum + slide.minutes, 0)).toBe(45);
    expect(track.slides.filter(slide => slide.backup).length).toBeGreaterThanOrEqual(4);
    expect(new Set(track.slides.map(slide => slide.slug)).size).toBe(track.slides.length);
    expect(track.slides[0].slug).toBe("opening");
    expect(track.slides[track.mainCount - 1].slug).toBe("questions");
    expect(track.revision).toBe("8781b41982b7431dade1ebe3d37fc132fbf29d0d");
  }
});

for (const trackId of trackIds) {
  test(`${trackId}: every slide, source excerpt, note and replay fits offline`, async ({ page }, testInfo) => {
    const track = await page.evaluate(id => window.presentationManifest.find(item => item.id === id), trackId);
    await page.locator(`#track-menu a[href="#/${trackId}/opening"]`).click();
    await expect(page.locator("#deck")).toBeVisible();
    const checkedSnippets = new Set();
    const directory = resolve(artifacts, testInfo.project.name, trackId);
    if (process.env.CAPTURE_SLIDES) await mkdir(directory, { recursive: true });
    for (const [index, slide] of track.slides.entries()) {
      await navigate(page, trackId, slide.slug);
      const active = page.locator("#stage .slide:not([hidden])");
      expect.soft(await active.evaluate(layoutProblems), `${trackId}/${slide.slug}`).toEqual([]);
      await expect(page.locator("#notes-dialog")).not.toBeVisible();
      const body = await active.locator(".slide-body").innerText();
      expect(body.length).toBeGreaterThan(50);
      const notes = await page.locator("#notes-content").innerText();
      expect(notes.length).toBeGreaterThan(450);
      expect(await page.locator("#notes-content a").count()).toBeGreaterThan(0);
      for (const code of await active.locator("[data-source-file]").all()) {
        const path = await code.getAttribute("data-source-file");
        const id = await code.getAttribute("data-snippet");
        if (!checkedSnippets.has(id)) {
          expect(path).toMatch(/^(langchain4j|spring-ai|embabel)\/src\/(main|test)\/java\//);
          const source = await readFile(resolve(root, path), "utf8");
          expect.soft(normalize(source), `${trackId}/${slide.slug}: verbatim excerpt ${id}`).toContain(normalize(await code.textContent()));
          checkedSnippets.add(id);
        }
        const size = await code.evaluate(element => {
          const stage = document.querySelector("#stage");
          const scale = stage.getBoundingClientRect().width / 1280;
          return parseFloat(getComputedStyle(element).fontSize) * scale;
        });
        expect(size).toBeGreaterThanOrEqual(22);
        const splitIdentifiers = await code.evaluate(element => {
          const split = [];
          const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
          for (let node = walker.nextNode(); node; node = walker.nextNode()) {
            for (const match of node.textContent.matchAll(/\b[A-Za-z_$][\w$]*\b/g)) {
              const range = document.createRange();
              range.setStart(node, match.index);
              range.setEnd(node, match.index + match[0].length);
              const rows = new Set([...range.getClientRects()].filter(rect => rect.width > 0)
                .map(rect => Math.round(rect.top)));
              if (rows.size > 1) split.push(match[0]);
            }
          }
          return split;
        });
        expect.soft(splitIdentifiers, `${trackId}/${slide.slug}: never wrap inside an identifier`).toEqual([]);
      }
      const replay = active.locator("[data-demo]");
      if (await replay.count()) {
        for (const step of await replay.locator("[data-demo-step]").all()) {
          await step.click();
          await expect(step).toHaveAttribute("aria-pressed", "true");
          await expect(replay.locator("[data-demo-panel]:not([hidden])")).toHaveCount(1);
          expect.soft(await active.evaluate(layoutProblems), `${trackId}: replay ${await step.innerText()}`).toEqual([]);
        }
        await replay.locator("[data-demo-step]").first().click();
      }
      if (process.env.CAPTURE_SLIDES) {
        await active.screenshot({ path: resolve(directory, `${String(index + 1).padStart(2, "0")}-${slide.slug}.png`) });
      }
    }
    if (process.env.CAPTURE_SLIDES) {
      await writeFile(resolve(directory, "manifest.json"), JSON.stringify(track, null, 2));
    }
  });
}

test("click and keyboard navigation, index, backups, history and invalid routes", async ({ page }) => {
  for (const id of trackIds) {
    await page.goto(presentation);
    await page.locator(`#track-menu a[href="#/${id}/opening"]`).click();
    await expect(page.locator("#slide-count")).toHaveText("1 / 24");
    await page.getByRole("button", { name: "Next", exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`#/${id}/route$`));
    await page.keyboard.press("PageDown");
    await expect(page).toHaveURL(new RegExp(`#/${id}/request-contract$`));
    await page.keyboard.press("ArrowLeft");
    await expect(page).toHaveURL(new RegExp(`#/${id}/route$`));
    await page.keyboard.press("Home");
    await page.keyboard.press("Space");
    await expect(page).toHaveURL(new RegExp(`#/${id}/route$`));
    await page.keyboard.press("End");
    await expect(page).toHaveURL(new RegExp(`#/${id}/questions$`));
    await expect(page.locator("#next")).toBeDisabled();
    await page.keyboard.press("ArrowRight");
    await expect(page).toHaveURL(new RegExp(`#/${id}/questions$`));
    await page.keyboard.press("o");
    await expect(page.locator("#outline-dialog")).toBeVisible();
    await page.locator(`#outline-links a[href="#/${id}/versions"]`).click();
    await expect(page.locator("#slide-count")).toContainText("Backup 1");
    await page.keyboard.press("ArrowRight");
    await expect(page.locator("#slide-count")).toContainText("Backup 2");
    await page.keyboard.press("Home");
    await page.keyboard.press("m");
    await expect(page.locator("#menu")).toBeVisible();
    await page.goBack();
    await expect(page.locator("#deck")).toBeVisible();
    await page.reload();
    await expect(page.locator("#stage .slide:not([hidden])")).toHaveAttribute("data-slide-id", "opening");
  }
  for (const bad of ["missing/opening", "comparison/missing", "__proto__/opening", "comparison/opening/extra"]) {
    await page.goto(`${presentation}#/${bad}`);
    await expect(page.locator("#menu")).toBeVisible();
    await expect(page.locator("#status")).toContainText("Unknown track or slide");
  }
});

test("notes stay hidden, focus stays in dialogs, theme toggles and fullscreen really enters", async ({ page }) => {
  await navigate(page, "comparison", "opening");
  await expect(page.locator("#notes-dialog")).not.toBeVisible();
  await page.keyboard.press("n");
  await expect(page.locator("#notes-dialog")).toBeVisible();
  await expect(page.locator("#notes-meta")).toContainText("talk clock 0-1 min");
  await page.getByRole("button", { name: "Next notes", exact: true }).click();
  await expect(page.locator("#notes-meta")).toContainText("talk clock 1-2 min");
  for (let i = 0; i < 12; i++) {
    await page.keyboard.press("Tab");
    expect(await page.evaluate(() => Boolean(document.activeElement.closest("#notes-dialog")))).toBe(true);
  }
  await page.keyboard.press("Escape");
  await expect(page.locator("#notes-dialog")).not.toBeVisible();
  await expect(page.locator("#deck")).toBeVisible();
  const oldTheme = await page.locator("html").getAttribute("data-theme");
  await page.keyboard.press("t");
  expect(await page.locator("html").getAttribute("data-theme")).not.toBe(oldTheme);
  await page.reload();
  expect(await page.locator("html").getAttribute("data-theme")).not.toBe(oldTheme);
  await page.getByRole("button", { name: "Fullscreen", exact: true }).click();
  await expect.poll(() => page.evaluate(() => document.fullscreenElement?.tagName)).toBe("HTML");
  await page.getByRole("button", { name: "Exit fullscreen", exact: true }).click();
  await expect.poll(() => page.evaluate(() => document.fullscreenElement)).toBeNull();
  expect(await page.evaluate(() => matchMedia("(prefers-reduced-motion: reduce)").matches)).toBe(true);
  await page.keyboard.press("?");
  await expect(page.locator("#help-dialog")).toBeVisible();
  await page.getByRole("button", { name: "Close help", exact: true }).click();
});

test("slide and speaker printing use every selected page, including optional backups", async ({ page }, testInfo) => {
  await page.evaluate(() => { window.print = () => { window.printRequested = true; }; });
  for (const trackId of trackIds) {
    await navigate(page, trackId, "opening");
    for (const format of ["slides", "notes"]) {
      await page.getByRole("button", { name: "Print", exact: true }).click();
      await page.locator("#print-format").selectOption(format);
      await page.locator("#print-backups").uncheck();
      await page.getByRole("button", { name: "Print / save PDF", exact: true }).click();
      expect(await page.evaluate(() => window.printRequested)).toBe(true);
      const selector = format === "slides" ? ".printed-slide" : ".print-handout";
      await expect(page.locator(`#print-deck ${selector}`)).toHaveCount(24);
      await page.emulateMedia({ media: "print" });
      await expect(page.locator("#deck")).not.toBeVisible();
      await expect(page.locator("#print-deck")).toBeVisible();
      await expect(page.locator(`#print-deck ${selector}`).last()).toContainText("What should your framework own?");
      if (process.env.CAPTURE_SLIDES && testInfo.project.name === "light-720") {
        const directory = resolve(artifacts, "print");
        await mkdir(directory, { recursive: true });
        const pdf = await page.pdf({ path: resolve(directory, `${trackId}-${format}.pdf`), preferCSSPageSize: true, printBackground: true });
        expect(pdf.toString("latin1").match(/\/Type\s*\/Page\b/g)?.length, `${trackId} ${format} PDF page count`).toBe(24);
      }
      await page.emulateMedia({ media: "screen" });
    }
    await page.getByRole("button", { name: "Print", exact: true }).click();
    await page.locator("#print-backups").check();
    await page.getByRole("button", { name: "Print / save PDF", exact: true }).click();
    const expected = await page.evaluate(id => window.presentationManifest.find(track => track.id === id).slides.length, trackId);
    await expect(page.locator("#print-deck .print-handout")).toHaveCount(expected);
  }
});
