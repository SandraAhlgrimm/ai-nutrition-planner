# Browser conference talks

Open **[`index.html`](index.html) in a local browser**. On GitHub, download the
file first; GitHub's source viewer does not run it. The entire presentation is
one self-contained HTML file: no build, CDN, external fonts, model service,
login, credentials or network connection is needed to present.

The menu offers four independently presentable English talks for Java/Spring
developers, with Sandra Ahlgrimm as presenter. The LangChain4j, Spring AI and
Embabel talks each have **24 main slides timed to 45 minutes**; the comparison
has **25 main slides at the same duration**. All include a three-minute
synthetic demo and a one-minute question slot, plus **five optional backup
slides** outside that timing. The comparison opens in an Øredev context, credits
Timo Salm's AI Recipe Finder as the topic inspiration, and introduces Sandra.
The other tracks retain their general-purpose openings.

| Track | Opening deep link (append to `index.html`) | Story |
| --- | --- | --- |
| Framework comparison | `#/comparison/opening` | Øredev 2026 session, **“Comparing Agentic AI Frameworks for Java”**; topic inspiration from Timo Salm's AI Recipe Finder; speaker intro; one contract; native graphs, application orchestration and typed planning; feature-ownership table, tools, humans, skills, MCP and opt-in labs. |
| LangChain4j | `#/langchain4j/opening` | Typed native workflows and invocation-local tools; deterministic acceptance; official Java SDK MCP bridge; experimental decision/cascade/mapper labs. |
| Spring AI | `#/spring-ai/opening` | ChatClient and native tool advisors; application refinement/routing/workers; community skills/questions; native RC sessions, compaction and tool confirmation. |
| Embabel | `#/embabel/opening` | Baseline GOAP and audit states; trusted skills, unfolding tools and scoped identity; separate experimental decisions, native Supervisor and Hybrid labs. |

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
variables, with a dark-violet accent on light surfaces and a lighter violet
variant for contrast on dark surfaces. Fonts use local fallbacks, so small typographic differences between
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

The final replay is slide 22 in the framework comparison and slide 21 in the
other tracks. It is an interactive **synthetic replay**, explicitly labeled
as such. Select its panels to explain the request, a rejected initial
candidate, an audited repair at both inclusive calorie bounds and the exhaustion branch. The Spring AI replay
also includes a browser-only cooking-preference answer.

These are explanatory states, not recordings, benchmark results, full API
payloads or evidence of model quality. The replay requires no live application.
An optional live demo can use a separately prepared local sample, but it is not
needed for any talk. Do not project environment files, credentials, private
profiles, cookies, logs or traces. The repository's public login is demo-only,
not production security guidance.

## Source and scope

Content was checked against merged `main` on **November 4, 2026**, at
[`8d620eef6689a3fed76aa99cc30ab1054b85b1c3`](https://github.com/SandraAhlgrimm/ai-nutrition-planner/commit/8d620eef6689a3fed76aa99cc30ab1054b85b1c3).
Repository links in notes and excerpt captions pin that commit. Upstream links
pin LangChain4j **1.23.0** (agentic/patterns and Boot 4 starters
**1.23.0-beta33**, experimental), Spring AI **2.1.0-RC1** (release candidate),
community agent-utils **0.13.0**, and Embabel **1.5.4** (new decision APIs
experimental), rather than moving branches. LangChain4j and Embabel use
Boot **4.1.2** / Spring **7.0.10**; the Spring AI sample deliberately uses
Boot **4.2.0-RC1** / Spring **7.1.0-RC1** for native session/confirmation APIs.
Embabel's provider bridge stays on Spring AI **2.0.2**. Do not infer version
parity or GA stability from a shared REST contract.

Visible code panels are actual repository excerpts, with only indentation
normalized; partial method excerpts are not standalone compilable programs.
Display-only breaks at call/argument boundaries preserve identifiers and use
hanging indentation; they do not change the copied source text.
Code panels use an embedded **IntelliJ IDEA Darcula-inspired** visual language:
dark charcoal panels, orange keywords, green strings, yellow annotations and
method names, blue numbers, muted-violet JSON keys, and gray comments. Colors are adjusted for readable
contrast; this is not a pixel-identical IDE theme or a copy of proprietary assets.
The small built-in Java/JSON tokenizer needs no CDN, package or model service.
Literal prompt excerpts stay plain text rather than receiving misleading Java
tokens. The same code palette is used in both slide themes and print handouts;
the surrounding slides retain their violet accents.
Conceptual diagrams and synthetic replay states are labeled or explained in
notes and are not presented as execution traces. Source links need a network
connection if opened, but all presenting content is already embedded.

The shared invariant is an initial candidate plus at most three revisions, with
every candidate audited. Model approval cannot override deterministic
request-shape or per-day calorie checks. Each requested day must independently
total **90%-110% of its profile target, inclusive**. All three demo profiles
target **2,500 kcal/day**, accepting **2,250-2,750 kcal** per requested day.
Days cannot offset each other. The target is not prorated for partial-day meal
requests: the tiny one-meal-per-day replay intentionally makes the contract
easy to see, but can imply unusually large meals. Prefer all three meals for
a more natural live demo. Exhaustion is an explicit error, including HTTP 422 for REST.
Logical candidates/audits are distinct from tool turns and provider retries.
Nutrition estimates and dietary judgments remain model-dependent: this is not a
clinically validated nutrition service. The calorie range is a demo acceptance
policy over model-derived estimates, not clinical advice or allergy certification.

All three samples now expose authenticated Streamable HTTP `/mcp`.
LangChain4j uses an **application bridge to official Java MCP SDK 2.1.0**,
not a LangChain4j-native HTTP server. Spring AI uses native `@McpTool` and
Embabel native remote goal export. MCP schemas/results are not interchangeable.

The existing slide routes now include separate **opt-in pattern labs**, without
adding slides or time. Enable `patterns` alongside exactly one provider.
Authenticated lab routes return 404 with it off; startup/catalog reads make no
provider calls or model downloads. Lab results are culinary illustrations,
not audited `WeeklyPlan` responses. LangChain4j decisions require an explicitly
configured compatible decision model (Azure is mapper-only), and its cascade
has an application final-tier judgment. Spring AI routing/workers are application
composition; sessions/whole-turn compaction and tool confirmation are native
2.1 RC APIs. Ownership, expiry, capacity and approval replay protection remain
application concerns. Embabel classification does not execute a specialist;
Supervisor and Hybrid use request-local scopes and bounded model/tool work,
distinct from baseline GOAP. See each lab slide's notes for limits and source
links. Counts are not transport-attempt, token, billing or medical guarantees.

The talks distinguish **framework**, **application** and **community** ownership.
“Not demonstrated” does not mean “unsupported.” No benchmark, conference date,
testimonial, deployment or publishing service is implied. The historical
[`slides.pdf`](../slides.pdf) is unchanged.

The comparison talk uses the event name **Øredev 2026** without implying a
schedule slot or separate event-branded title. Its speaker slide includes
Sandra Ahlgrimm's role, Java/AI/JetBrains focus and GitHub profile. The portrait
is embedded in the HTML for offline use and comes from
[`SandraAhlgrimm/SandraAhlgrimm`](https://github.com/SandraAhlgrimm/SandraAhlgrimm),
[`sandra-speaker.jpg`](https://raw.githubusercontent.com/SandraAhlgrimm/SandraAhlgrimm/main/sandra-speaker.jpg).
The existing `#/comparison/route` deep link remains stable and opens the
speaker introduction; `#/comparison/origin-story` identifies the new source
and topic-progression slide. Its attribution is based on Sandra's confirmation
that this repository inspired the topic. The linked upstream README describes
a Spring Boot demo of Java AI frameworks, including LangChain4j, archived
Semantic Kernel, external Spring AI examples, Function Calling and RAG. This
is not a claim of a fork, code reuse, author endorsement or personal timeline.
The verified README snapshot is
[`9a7558f`](https://github.com/timosalm/ai-recipe-finder/commit/9a7558fe82b111f3b3983ec0ec3fa7df3e83d7fc).
The added one-minute slide is balanced by reducing the comparison's
observability slide to one minute, keeping the 45-minute talk duration intact.

The opening slide now uses the official Øredev 2026 session title,
**“Comparing Agentic AI Frameworks for Java.”** It was verified against the
official website's JavaScript-linked Sessionize `Sessions` feed:
[`https://sessionize.com/api/v2/y2gp6i8d/view/Sessions`](https://sessionize.com/api/v2/y2gp6i8d/view/Sessions).
Sandra supplied the session abstract, which is reproduced verbatim in the
opening slide's speaker notes.

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
It also guards current release/source pins, per-day calorie policy, feature
ownership, lab isolation and retained slide counts/routes.
Code identifiers must remain intact when a line wraps.
It rejects runtime/console errors and any remote asset request.

To retain rendered slides and PDFs outside the repository:

```sh
QA_DIR=/absolute/path/to/deck-qa CAPTURE_SLIDES=1 npm test
node tests/contact-sheets.mjs /absolute/path/to/deck-qa
```

The capture run also checks the generated main-talk PDFs have 25 pages for the
comparison and 24 for each other track. Contact sheets combine four rendered
slides each and retain links by filename to the full-size captures. A geometry
check is not a substitute for visual review:
inspect the rendered images, including code wrapping, spacing, contrast and
legibility, then fix and re-render. Presentation screenshots and test reports
should not be committed.
