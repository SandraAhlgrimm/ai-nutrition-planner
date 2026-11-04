import { defineConfig } from "@playwright/test";
import { resolve } from "node:path";

export default defineConfig({
  testDir: "./tests",
  testMatch: "deck.spec.mjs",
  fullyParallel: false,
  workers: 2,
  timeout: 120_000,
  outputDir: process.env.QA_DIR ? resolve(process.env.QA_DIR, "test-results") : "./test-results",
  reporter: "list",
  use: {
    browserName: "chromium",
    locale: "en-US",
    reducedMotion: "reduce",
    screenshot: "only-on-failure"
  },
  projects: [
    { name: "light-720", use: { viewport: { width: 1280, height: 720 }, colorScheme: "light" } },
    { name: "dark-720", use: { viewport: { width: 1280, height: 720 }, colorScheme: "dark" } },
    { name: "light-1080", use: { viewport: { width: 1920, height: 1080 }, colorScheme: "light" } },
    { name: "dark-1080", use: { viewport: { width: 1920, height: 1080 }, colorScheme: "dark" } }
  ]
});
