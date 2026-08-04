# Hiresemble Product Design System

## Product Context

Hiresemble is a Korean B2C job-preparation product. It turns user-approved career evidence into job-posting analysis, cover-letter support, and interview preparation. The product must feel trustworthy, practical, and already operational—not like an AI concept mockup or marketing landing page.

Primary target for this design round: the authenticated job-analysis result route `/jobs/:jobId/analysis`.

User job to be done:
1. Understand whether the saved evidence supports applying to this job.
2. See the fit score and eligibility with the limitations of that score.
3. Distinguish verified strengths, partial matches, missing evidence, and unverified requirements.
4. Decide the next concrete action, usually continuing to a cover letter or strengthening profile evidence.
5. Inspect criterion-level evidence without losing the summary context.

## Product Architecture

- Global authenticated shell: compact top navigation on desktop, fixed bottom navigation on mobile.
- Job workspace shell: company/position context and workflow tabs for overview, analysis, cover letter, and interview.
- Analysis content: current run state, decision summary, source job requirements, strength and gap insights, criterion filters and evidence details, analysis history.
- Existing API values, business decisions, state transitions, accessible names, links, pagination, disclosures, and responsive behavior must remain intact.

## Brand Foundation

### Color

Use only the established semantic tokens from `frontend/src/styles/main.css`.

- Primary action and selected state: `--color-brand` / Hiresemble blue `#3157ff`.
- Hover/active: `--color-brand-hover` and `--color-brand-active`.
- Main canvas: `--color-canvas` `#f7f8fc`.
- Surfaces: white and `--color-surface-subtle`; do not create tinted section backgrounds.
- Text: `--color-ink`, `--color-ink-soft`, `--color-muted`.
- Borders: `--color-border`, with `--color-border-strong` only for stronger separation.
- Success, warning, danger, and info colors are reserved for genuine semantic status, shown as compact text, icon, border, or small badge—not large color-filled panels.
- Never introduce mint, khaki, yellow-beige, cream, purple, pink, neon, aurora, glass, or gradient palettes.

The interface must remain understandable in grayscale. Color supports an existing hierarchy; it never creates the hierarchy alone.

### Typography

Use only the established system stack:
`-apple-system, BlinkMacSystemFont, Segoe UI, Noto Sans KR, Apple SD Gothic Neo, Malgun Gothic, sans-serif`.

- Page/report title: 24–32px, compact line height, 700–800 weight.
- Section title: 17–20px, 700 weight.
- Key numeric result: 32–48px only where the fit score is the actual dominant result.
- Body: 15–16px, high contrast, line height 1.55–1.7.
- Supporting/meta text: 12–14px, never below accessible contrast.
- Avoid oversized display headings, decorative fonts, vague slogans, and promotional copy.

### Spacing and Geometry

Use the existing 4/8/12/16/20/24/32/40/48/64px spacing scale.

- Default control radius: 10px.
- Default surface radius: 16px, but use surfaces selectively.
- Lists, table-like rows, dividers, grouped sections, and aligned columns should carry most of the report.
- Avoid repeating a card for every section or every sentence.
- Avoid pill containers except true compact status/filter controls.
- Content height follows content; do not pad cards to artificial equal height.
- Shadows are exceptional. Prefer spacing, alignment, 1px borders, and typography.

### Layout

- Main content width follows the existing 1180px token and job-detail shell.
- The analysis page is a decision report, not a statistics dashboard.
- Maintain one obvious primary action: proceed to cover-letter work when analysis is complete.
- Secondary actions (reanalyze, view source, retry, inspect history) remain lower emphasis.
- The first viewport must answer: current status, fit/eligibility, what this means, and what to do next.
- Dense evidence belongs below the decision summary and supports scanning through filters, grouped rows, dividers, and disclosure.
- Related facts sit adjacent; avoid decorative blank space and independent floating tiles.

## Required Design Directions

All three branches must preserve the same real Korean content and the same functional contract while changing information architecture—not merely color.

### A — Restrained Product UI

- White and neutral-gray dominant.
- Compact report header followed by high-density rows and dividers.
- Minimal color area, few surfaces, crisp typography.
- Efficient desktop scanning without feeling compressed.

### B — User-Friendly B2C UI

- A clear current-state narrative connects the decision to one next action.
- Plain, concrete Korean copy.
- Cards only where they genuinely group a decision or action.
- Trustworthy and approachable, not playful or overly casual.
- Responsive mobile flow intentionally reprioritizes content.

### C — Data-Centered Analysis UI

- Explicit relationship among score, eligibility, strengths, gaps, evidence, and next action.
- Dense desktop comparison using aligned columns, table/list structures, sticky/local navigation where appropriate.
- Long criterion analysis is searchable and scannable without repeated boxes.
- Mobile converts comparisons into compact labeled rows and progressive disclosure rather than blindly stacking desktop panels.

## Prohibited AI-Generated Patterns

- A separate rounded card around every block.
- Identical box weight for every fact.
- Repeating numbered circles, decorative badges, or icons without meaning.
- Pastel color zoning or large semantic color fills.
- Gradients, glassmorphism, glow, abstract shapes, illustrations used as filler.
- Huge hero copy and excessive blank space.
- Fake metrics, invented functionality, placeholder content, or Lorem ipsum.
- Multiple competing primary actions.
- Landing-page or portfolio-storytelling composition inside the authenticated product.
- Copying Toss, Linear, Jumpit, Jasoseol, or other brands directly.

## Interaction and Accessibility

- Preserve semantic headings, buttons, links, labels, disclosure state, pagination, keyboard focus, and live status.
- Use existing `AppIcon`, `StatusBadge`, `StatePanel`, and `PaginationNav` behavior where appropriate.
- Touch targets should be at least 44px on mobile.
- Focus uses the existing blue focus ring.
- Reduced-motion preferences must be respected.
- Animation is limited to short state transitions, progress indication, and disclosure; it never delays access to information.

## Responsive Rules

### Desktop

- Use available width for aligned comparison and a stable reading rhythm.
- Keep job context and workflow navigation visible above the report.
- Prefer a two-column summary only when each column has a distinct decision purpose.
- Criteria and evidence can use dense row/table patterns with clear headers.
- Avoid full-width long lines; narrative copy should stay near the reading-width token.

### Mobile

- First show current analysis state, fit/eligibility, concise interpretation, and the primary action.
- Move score methodology, source requirements, criterion details, and history into lower-priority sections or disclosures.
- Use horizontal scrolling only for compact filter tabs, not for primary content.
- Convert comparison columns into labeled rows with persistent meaning.
- Remove decorative/redundant content before stacking.
- Prevent overflow at 320–390px and preserve readable text wrapping.

## Content Fidelity

Use content already present in the source page and fixtures. Do not invent metrics, unsupported claims, capabilities, or job data. Suitable examples include:

- `등록한 정보와 공고 요구사항의 일치도`
- `지원 가능 여부`
- `강점`, `보완 필요`, `추가 확인`
- `자기소개서 준비로 이동`
- Existing company, position, criterion, evidence, coverage, and run-status strings from the Vue source.

## Design Evaluation

Select the final direction by evidence:
1. Current state and primary action are immediately clear.
2. Strengths and gaps remain distinct without color.
3. Long analysis copy is easy to scan and inspect.
4. Repeated card/container patterns are minimized.
5. Hierarchy comes from type, spacing, alignment, and dividers.
6. Desktop and mobile extensions are credible.
7. The result integrates with the existing Hiresemble shell and tokens.
8. The screen feels like a maintained B2C product, not an AI-generated SaaS template.

