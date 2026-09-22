# Browser conference talks

Open **[`index.html`](index.html) in a local browser**. On GitHub, download the
file first; GitHub's source viewer does not run it. The entire presentation is
one self-contained HTML file: no build, CDN, external fonts, model service,
login, credentials or network connection is needed to present.

The menu offers four independently presentable English talks for Java/Spring
developers, with Sandra Ahlgrimm as presenter. Each has **24 main slides timed
to 45 minutes**, including a three-minute synthetic demo and a one-minute
question slot. Each also has **five optional backup slides** outside that timing.

| Track | Opening deep link (append to `index.html`) | Story |
| --- | --- | --- |
| Framework comparison | `#/comparison/opening` | One contract; explicit graphs, application orchestration and typed planning; tools, humans, skills and MCP; choose ownership rather than checkboxes. |
| LangChain4j | `#/langchain4j/opening` | Typed agents; native parallel/sequence/conditional/loop composition; invocation-local tool state; shape checks, final acceptance and observability. |
| Spring AI | `#/spring-ai/opening` | ChatClient and native tool advisors; application refinement; community skills/questions; answer propagation, browser cancellation and authenticated MCP. |
| Embabel | `#/embabel/opening` | Typed GOAP actions and concurrent scheduling; audit/revision states; trusted skills and unfolding tools; provider/schema migration lessons and scoped identity. |

## Presenting

Use the visible controls or these keys:

| Action | Keys |
| --- | --- |
| Next / previous slide | Right / Left, PageDown / PageUp, Space / Shift+Space |
| Main opening / questions | Home / End |
| Fullscreen | F |
| Speaker notes | N |
| Slide index and optional backups | O |
| Return to talk menu | M |
| Light / dark theme | T |
| Print slides or speaker handout | P |
| Help | ? |

Escape closes a dialog, exits fullscreen, or returns to the menu. Tab focus is
visible on controls and contained in open dialogs. Navigation stops at the main
questions slide; choose a backup explicitly in the index. The hash identifies a
stable track and slide, so a copied URL, reload or browser history restores it.

The default theme follows the operating system. To pin a theme, use
`index.html?clawpilotTheme=light#/comparison/opening` (or `dark`). Theme changes
update that query parameter. All presentation colors use the embedded Clawpilot
variables. Fonts use local fallbacks, so small typographic differences between
operating systems are expected. Reduced-motion preferences are respected.

**Notes are not a private presenter window.** They stay hidden until requested,
but pressing N shows them on the current screen. Close them before projecting,
or print a separate speaker handout. Every slide has timing, explanation,
transition, demo/audience cues and repository or pinned upstream links. Notes
show the cumulative talk clock; they do not run a timer.

Print offers audience slides (one 16:9 page per slide) or an A4 speaker handout.
Backups are excluded unless selected. Use the browser's print dialog to save a
PDF, disable browser-added headers/footers and preserve background graphics.
Printed audience slides show the initial state of interactive replays; the
speaker handout explains the alternative outcomes.

## Demo without conference infrastructure

Slide 21 in every track is an interactive **synthetic replay**, explicitly
labeled as such. Select its panels to explain the request, a rejected initial
candidate, an audited repair and the exhaustion branch. The Spring AI replay
also includes a browser-only cooking-preference answer.

These are explanatory states, not recordings, benchmark results, full API
payloads or evidence of model quality. The replay requires no live application.
An optional live demo can use a separately prepared local sample, but it is not
needed for any talk. Do not project environment files, credentials, private
profiles, cookies, logs or traces. The repository's public login is demo-only,
not production security guidance.

## Source and scope

Content is based on the final merged framework refresh at
[`8781b41982b7431dade1ebe3d37fc132fbf29d0d`](https://github.com/SandraAhlgrimm/ai-nutrition-planner/commit/8781b41982b7431dade1ebe3d37fc132fbf29d0d).
Repository links in notes and excerpt captions pin that commit. Upstream links
pin LangChain4j 1.20.0, Spring AI 2.0.1, community agent-utils 0.12.0 and Embabel
1.5.2 rather than moving main branches.

Visible code panels are actual repository excerpts, with only indentation
normalized; partial method excerpts are not standalone compilable programs.
Display-only breaks at call/argument boundaries preserve identifiers and use
hanging indentation; they do not change the copied source text.
Conceptual diagrams and synthetic replay states are labeled or explained in
notes and are not presented as execution traces. Source links need a network
connection if opened, but all presenting content is already embedded.

The shared invariant is an initial candidate plus at most three revisions, with
every candidate audited. Deterministic request-shape checks cannot be overridden
by model approval. Exhaustion is an explicit error, including HTTP 422 for REST.
Logical candidates/audits are distinct from tool turns and provider retries.
Nutrition estimates and dietary judgments remain model-dependent: this is not a
clinically validated nutrition service.

The talks distinguish **framework**, **application** and **community** ownership.
“Not demonstrated” does not mean “unsupported.” No benchmark, conference date,
testimonial, deployment or publishing service is implied. The historical
[`slides.pdf`](../slides.pdf) is unchanged.

## Editing and verification

`index.html` contains theme/CSS, pinned references, verbatim code snippets, shared
slide helpers, four track definitions and a small browser controller. There is
no runtime framework or build step. Keep slide IDs stable; change an existing
ID only if intentionally breaking its deep link. Main timing is checked to sum
to 45 minutes per track.

Node dependencies are **test-only**:

```sh
cd presentations
npm ci
npx playwright install chromium
npm test
```

The Playwright suite opens the real file with the browser offline. It covers all
four menu choices and every slide, both themes at 1280x720 and 1920x1080,
navigation/history/deep links, actual fullscreen, hidden notes/dialog focus,
replay steps, print layouts, source-excerpt fidelity, text overflow and overlap.
Code identifiers must remain intact when a line wraps.
It rejects runtime/console errors and any remote asset request.

To retain rendered slides and PDFs outside the repository:

```sh
QA_DIR=/absolute/path/to/deck-qa CAPTURE_SLIDES=1 npm test
node tests/contact-sheets.mjs /absolute/path/to/deck-qa
```

The capture run also checks the generated main-talk PDFs have 24 pages. Contact
sheets combine four rendered slides each and retain links by filename to the
full-size captures. A geometry check is not a substitute for visual review:
inspect the rendered images, including code wrapping, spacing, contrast and
legibility, then fix and re-render. Presentation screenshots and test reports
should not be committed.
