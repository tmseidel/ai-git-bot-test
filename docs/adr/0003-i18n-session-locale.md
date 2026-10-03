## ADR-0003: i18n via SessionLocaleResolver + `?lang=`

**Status:** Accepted

**Context**
The application must be switchable between German and English from the menu,
and **all** text (labels, placeholders, buttons, validation messages, error
responses) must be externalized.

**Options Considered**
1. **SessionLocaleResolver + `LocaleChangeInterceptor` (`?lang=de`)** — locale
   stored in the HTTP session; a visible EN/DE toggle links to `?lang=…`.
   - ✅ Pros: explicit, user-controllable, survives navigation; no client JS
     needed for the switch; simple and predictable.
   - ❌ Cons: state kept server-side (session) — not per-browser if sessions
     are shared.
2. **AcceptHeaderLocaleResolver** — locale inferred from the browser's
   `Accept-Language` header.
   - ✅ Pros: zero UI needed.
   - ❌ Cons: not user-controllable from the UI (the requirement is a menu
     switch); ambiguous when the browser language differs from the user's
     preference.
3. **CookieLocaleResolver** — locale in a cookie.
   - ✅ Pros: survives session loss.
   - ❌ Cons: more moving parts for a local single-user app.

**Decision**
We choose **SessionLocaleResolver** (default English, `setFallbackToSystemLocale`
off) with a `LocaleChangeInterceptor` bound to `?lang=`. The base template renders
the EN/DE toggle as links to `/?lang=en` / `/?lang=de`, and the active language is
highlighted. All strings live in `messages.properties` (EN, default) and
`messages_de.properties` (DE); adding a language is a new properties file — no code
change.

**Consequences**
- All UI text, Bean Validation messages (`#{...}`) and `ProblemDetail` error
  messages resolve through the single `MessageSource`.
- Locale is per-session; a new browser tab/session starts in English.
- German file is served as UTF-8 (`spring.messages.encoding: UTF-8`).
