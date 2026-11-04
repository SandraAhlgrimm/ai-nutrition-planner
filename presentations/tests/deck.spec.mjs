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
  for (const selector of [".slide-head", ".slide-body", ".slide-footer", ".card", ".node", ".code-panel", ".stop", ".demo-panel", ".outcome", ".source-list a", ".speaker-photo-frame", ".origin-card"]) {
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
  for (const selector of [".cards", ".split", ".flow", ".budget", ".route", ".lane", ".hero-grid", ".speaker-layout", ".origin-grid", ".closing", ".source-list", ".code-label", ".demo-steps"]) {
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
    expect(track.mainCount).toBe(track.id === "comparison" ? 25 : 24);
    expect(track.minutes).toBe(45);
    expect(track.slides.filter(slide => !slide.backup).reduce((sum, slide) => sum + slide.minutes, 0)).toBe(45);
    expect(track.slides.filter(slide => slide.backup)).toHaveLength(5);
    expect(new Set(track.slides.map(slide => slide.slug)).size).toBe(track.slides.length);
    expect(track.slides[0].slug).toBe("opening");
    expect(track.slides[track.mainCount - 1].slug).toBe("questions");
    expect(track.revision).toBe("8d620eef6689a3fed76aa99cc30ab1054b85b1c3");
  }
});

test("merged facts remain consistent across talks, notes and source pins", async ({ page }) => {
  const html = await readFile(resolve(here, "../index.html"), "utf8");
  const readme = await readFile(resolve(here, "../README.md"), "utf8");
  for (const text of [html, readme]) {
    expect(text).not.toMatch(/8781b419|1\.20\.0|beta30|2\.0\.1\b|0\.12\.0|1\.5\.2\b|4\.1\.1\b/);
    expect(text).not.toMatch(/No MCP server|Neither is wired here/);
  }
  for (const [module, versions] of [
    ["langchain4j", ["4.1.2", "1.23.0"]],
    ["spring-ai", ["4.2.0-RC1", "2.1.0-RC1", "0.13.0"]],
    ["embabel", ["4.1.2", "1.5.4", "2.0.2"]]
  ]) {
    const pom = await readFile(resolve(root, module, "pom.xml"), "utf8");
    for (const version of versions) {
      expect(pom).toContain(`>${version}<`);
      expect(html).toContain(version);
      expect(readme).toContain(version);
    }
    const profile = await readFile(resolve(root, module, "src/main/resources/application.yaml"), "utf8");
    expect(profile).toMatch(/daily-calorie-target:\s*2500\b/);
  }
  for (const id of trackIds) {
    await navigate(page, id, "request-contract");
    await expect(page.locator("#stage .slide:not([hidden])")).toContainText("2,250-2,750 kcal inclusive");
    await expect(page.locator("#stage .slide:not([hidden])")).toContainText("No cross-day offset");
    await expect(page.locator("#notes-content")).toContainText("does not prorate");
    await navigate(page, id, "versions");
    for (const version of ["1.23.0", "beta33", "2.1.0-RC1", "0.13.0", "1.5.4", "2.0.2"]) {
      await expect(page.locator("#stage .slide:not([hidden])")).toContainText(version);
    }
    await navigate(page, id, id === "comparison" ? "choose-ownership" : id === "langchain4j" ? "beyond-this-graph" : "ownership");
    const notes = page.locator("#notes-content");
    for (const caveat of ["patterns", "404", "not the audited WeeklyPlan baseline", "no model calls", "transport retries"]) {
      await expect(notes).toContainText(caveat);
    }
  }
  await navigate(page, "comparison", "choose-ownership");
  await expect(page.getByRole("table", { name: "Framework feature ownership comparison" })).toContainText("Application routing + workers");
  await navigate(page, "langchain4j", "mcp-boundary");
  await expect(page.locator("#stage .slide:not([hidden])")).toContainText("Java MCP SDK 2.1.0");
  await expect(page.locator("#notes-content")).toContainText("not a LangChain4j-native HTTP server");
  const sourcePaths = [...html.matchAll(/https:\/\/github\.com\/[^"\s]+/g)].map(match => match[0]);
  expect(sourcePaths.every(url => !/\/(blob|tree)\/(main|master)\//.test(url))).toBe(true);
});

test("comparison opens at Øredev and introduces Sandra with an embedded portrait", async ({ page }) => {
  await navigate(page, "comparison", "opening");
  await expect(page.locator("#stage .slide:not([hidden])")).toContainText("Øredev");
  await navigate(page, "comparison", "origin-story");
  const originSlide = page.locator("#stage .slide:not([hidden])");
  await expect(originSlide).toContainText("Function Calling and RAG");
  await expect(originSlide).toContainText("One contract, three models");
  await expect(originSlide.locator('a[href="https://github.com/timosalm/ai-recipe-finder"]')).toBeVisible();
  await expect(page.locator("#notes-content")).toContainText("does not claim");
  await navigate(page, "comparison", "route");
  const speakerSlide = page.locator("#stage .slide:not([hidden])");
  await expect(speakerSlide).toContainText("Senior Developer Advocate at GitHub/Microsoft");
  await expect(speakerSlide).toContainText("Java, AI and GitHub Copilot integrations in JetBrains IDEs");
  await expect(speakerSlide.locator('a[href="https://github.com/SandraAhlgrimm"]')).toBeVisible();
  const portrait = speakerSlide.locator(".speaker-photo");
  await expect(portrait).toHaveAttribute("alt", "Sandra Ahlgrimm speaking at a developer conference");
  await expect(portrait).toHaveAttribute("src", /^data:image\/jpeg;base64,/);
  expect(await portrait.evaluate(image => image.naturalWidth)).toBeGreaterThan(0);
  await expect(page.locator("#notes-content")).toContainText("public speaker photo");
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
      for (const href of await page.locator("#notes-content a").evaluateAll(links => links.map(link => link.href))) {
        const source = href.match(/^https:\/\/github\.com\/SandraAhlgrimm\/ai-nutrition-planner\/blob\/([^/]+)\/(.+)$/);
        if (source) {
          expect(source[1]).toBe(track.revision);
          await readFile(resolve(root, source[2]), "utf8");
        }
      }
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
        const tokens = code.locator('[class^="token-"]');
        if (id === "saAuditAnswers") {
          await expect(tokens).toHaveCount(0);
        } else {
          expect(await tokens.count(), `${id}: syntax tokens are rendered`).toBeGreaterThan(0);
          const contrast = await code.evaluate(element => {
            const luminance = color => {
              const channels = color.match(/[\d.]+/g).slice(0, 3).map(Number).map(value => {
                const channel = value / 255;
                return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
              });
              return channels.reduce((sum, channel, index) => sum + channel * [0.2126, 0.7152, 0.0722][index], 0);
            };
            const background = luminance(getComputedStyle(element.closest(".code-panel")).backgroundColor);
            return [element, ...element.querySelectorAll('[class^="token-"]')].map(token => {
              const foreground = luminance(getComputedStyle(token).color);
              return (Math.max(foreground, background) + 0.05) / (Math.min(foreground, background) + 0.05);
            });
          });
          expect(Math.min(...contrast), `${id}: all code colors meet WCAG AA`).toBeGreaterThanOrEqual(4.5);
        }
        if (id === "lcAgent") {
          await expect(code.locator(".token-annotation").first()).toHaveText("@Agent");
          await expect(code.locator(".token-string").first()).toHaveText('"Creates an audited weekly nutrition plan"');
        }
        if (id === "saOrder") {
          await expect(code.locator(".token-comment")).toHaveText("// Audit completed candidates, never intermediate tool-call responses.");
          await expect(code.locator(".token-keyword", { hasText: /^return$/ })).toHaveCount(1);
        }
        if (id === "saClock") {
          await expect(code.locator(".token-method", { hasText: /^currentMonth$/ })).toHaveCount(1);
        }
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
      if (slide.slug === "request-contract") {
        const request = active.locator(".request-code");
        await expect(request.locator(".token-property", { hasText: /^"days"$/ })).toHaveCount(1);
        await expect(request.locator(".token-string", { hasText: /^"MONDAY"$/ })).toHaveCount(1);
        const json = JSON.parse(await request.textContent());
        expect(json.days.map(day => day.day)).toEqual(["MONDAY", "TUESDAY"]);
      }
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
    const mainCount = id === "comparison" ? 25 : 24;
    await expect(page.locator("#slide-count")).toHaveText(`1 / ${mainCount}`);
    await page.getByRole("button", { name: "Next", exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`#/${id}/${id === "comparison" ? "origin-story" : "route"}$`));
    if (id === "comparison") {
      await page.getByRole("button", { name: "Next", exact: true }).click();
      await expect(page).toHaveURL(new RegExp(`#/${id}/route$`));
    }
    await page.keyboard.press("PageDown");
    await expect(page).toHaveURL(new RegExp(`#/${id}/request-contract$`));
    await page.keyboard.press("ArrowLeft");
    await expect(page).toHaveURL(new RegExp(`#/${id}/route$`));
    await page.keyboard.press("Home");
    await page.keyboard.press("Space");
    await expect(page).toHaveURL(new RegExp(`#/${id}/${id === "comparison" ? "origin-story" : "route"}$`));
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
  for (const key of ["Tab", "Shift+Tab"]) {
    for (let i = 0; i < 12; i++) {
      await page.keyboard.press(key);
      expect(await page.evaluate(() => Boolean(document.activeElement.closest("#notes-dialog")))).toBe(true);
    }
  }
  await page.keyboard.press("Escape");
  await expect(page.locator("#notes-dialog")).not.toBeVisible();
  await expect(page.locator("#deck")).toBeVisible();
  const oldTheme = await page.locator("html").getAttribute("data-theme");
  const checkAccentContrast = async () => {
    const contrast = await page.evaluate(() => {
      const style = getComputedStyle(document.documentElement);
      const rgb = name => style.getPropertyValue(name).trim().match(/\w\w/g).map(value => parseInt(value, 16));
      const luminance = color => color.map(value => {
        const channel = value / 255;
        return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
      }).reduce((sum, channel, index) => sum + channel * [0.2126, 0.7152, 0.0722][index], 0);
      const ratio = (a, b) => (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
      const accent = luminance(rgb("--cp-accent"));
      const hover = luminance(rgb("--cp-accent-hover"));
      const foreground = luminance(rgb("--cp-accent-fg"));
      return [
        ratio(accent, luminance(rgb("--cp-bg"))),
        ratio(accent, luminance(rgb("--cp-surface"))),
        ratio(accent, foreground),
        ratio(hover, foreground)
      ];
    });
    for (const ratio of contrast) expect(ratio, "Accent text and buttons meet WCAG AA contrast").toBeGreaterThanOrEqual(4.5);
  };
  await checkAccentContrast();
  await page.keyboard.press("t");
  expect(await page.locator("html").getAttribute("data-theme")).not.toBe(oldTheme);
  await checkAccentContrast();
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
      const expectedMain = trackId === "comparison" ? 25 : 24;
      await expect(page.locator(`#print-deck ${selector}`)).toHaveCount(expectedMain);
      await page.emulateMedia({ media: "print" });
      await expect(page.locator("#deck")).not.toBeVisible();
      await expect(page.locator("#print-deck")).toBeVisible();
      await expect(page.locator(`#print-deck ${selector}`).last()).toContainText("What should your framework own?");
      if (process.env.CAPTURE_SLIDES && testInfo.project.name === "light-720") {
        const directory = resolve(artifacts, "print");
        await mkdir(directory, { recursive: true });
        const pdf = await page.pdf({ path: resolve(directory, `${trackId}-${format}.pdf`), preferCSSPageSize: true, printBackground: true });
        expect(pdf.toString("latin1").match(/\/Type\s*\/Page\b/g)?.length, `${trackId} ${format} PDF page count`).toBe(expectedMain);
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
