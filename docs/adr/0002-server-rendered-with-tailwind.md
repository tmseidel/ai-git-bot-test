## ADR-0002: Server-rendered Thymeleaf + Tailwind (Play CDN)

**Status:** Accepted

**Context**
The app is a small CRUD + timer UI. It needs a responsive, clean look (the user
asked for Tailwind) and two languages. A full SPA framework (React/Vue) would
add a Node build pipeline, routing, and state management far beyond the app's
size.

**Options Considered**
1. **Server-rendered Thymeleaf + Tailwind (Play CDN)** — pages rendered by
   Spring; a few vanilla-JS `fetch` calls for the start/stop buttons.
   - ✅ Pros: no separate frontend build; i18n is native via Thymeleaf `#{...}`;
     one codebase; trivially testable with MockMvc.
   - ❌ Cons: Play CDN is a dev/prototyping build (not intended for production
     scale); full page reloads on state changes.
2. **SPA (React/Vue) + REST API** — client-rendered.
   - ✅ Pros: rich interactivity; decoupled frontend.
   - ❌ Cons: Node toolchain; i18n must be re-implemented client-side;
     disproportionate for this scope.

**Decision**
We choose **server-rendered Thymeleaf + Tailwind via the Play CDN**. Start/stop
use small `fetch` POSTs to JSON endpoints and then reload; the live elapsed timer
ticks client-side without a server round-trip.

**Consequences**
- No Node build step in the Maven lifecycle; everything runs with `mvn`.
- i18n is handled once, server-side, in the properties bundles.
- If the UI grows (live multi-client updates, complex client state), revisit with
  a real frontend or a production Tailwind build (CLI) instead of the CDN.
