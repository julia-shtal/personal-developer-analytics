# Adding a metric

The ordered procedure for adding one `MetricType` to the platform, matching thesis §6.2.3.
Follow the steps in order; each one's coupling is named, together with whether an omission
is caught automatically (at application startup, by a test) or not caught at all. Where it
is not caught, the step depends on the three-layer testing discipline and review, not on a
drift guard.

Use the `add-metric` skill for the generative parts of this procedure (endpoint boilerplate,
frontend wiring). This document is the authoritative list of couplings; the skill's own
"Actual project layout" section predates the `MetricCalculator`/`MetricCalculatorRegistry`
architecture described below and should not be followed for step 4.

## 1. Define the metric

Write `docs/metrics/<slug>.md`: definition, formula, edge cases, attribution rule, bot
exclusion (state which case per the project's non-negotiable rule), validation plan. This
text is thesis-canonical — copied into the corresponding chapter verbatim, not reworded
after the fact.

**Coupling:** none yet — this file is read by nothing at runtime. **Not caught** if it is
skipped; it is a documentation obligation, checked only by review and by the thesis
cross-check in step 10.

## 2. `MetricType` enum constant

Add the constant to `metrics/model/MetricType.java`, in the category grouping that matches
its neighbours, with:

- `inAiContext` — true only if the AI summary should see it (step 6).
- `dailySum` — true only for a DAILY-shape metric that sums rather than averages over a
  window; must imply `inAiContext` (see step 8).
- `aggregatePeriod` — true only if the metric is recomputed per ISO week
  (`MetricsService.writesAggregatePeriod`, which dispatches the calculator's invocation
  cadence). This is **not** the same thing as "stored in AGGREGATE shape" — see step 5. A weekly
  metric left at `false` receives the raw requested range, not whole weeks, so its calculator must
  query whole ISO weeks itself (as `MergesToDefaultBranchCalculator` does); otherwise a week cut by a
  backfill chunk is stored with a partial count and overwritten by the next run.
- `unit` — non-blank; the CSV export column header.

**Coupling:** the enum constant itself. **Caught by test**
(`MetricTypeTest.everyMetric_declaresADisplayUnit` for a blank unit;
`dailySum_isSubsetOfInAiContext` / `aggregatePeriod_isSubsetOfInAiContext` for the two flag
subset rules). The three exact-count assertions in the same file
(`inAiContext_exactlyTwelveMetrics`, `dailySum_exactlyFiveMetrics`,
`aggregatePeriod_exactlyFiveMetrics`) will fail and must be updated by hand if the new
constant sets the corresponding flag — **caught by test**, but the fix is manual, not
automatic.

## 3. `metric_snapshots` table comment

Restate the `metric_snapshots` table comment in a new migration, listing the new type under
its DAILY or AGGREGATE section by storage shape (step 5), not by `aggregatePeriod`.
`COMMENT ON TABLE` has no partial form, so the full comment is restated, not patched — see
`V66__metric_snapshots_comment_refresh.sql` for the pattern. Migrations are append-only (the
project's schema non-negotiable); do not edit a prior comment migration.

**Coupling:** the comment text, read back from `pg_description` rather than duplicated in a
second checked-in list. **Caught by test** (`MetricSnapshotTableCommentTest`) —
`tableComment_bothSections_accountForEveryMetricType` fails for a type missing from both
sections regardless of shape, and the section-specific assertions fail if a type is placed in
the wrong section relative to `AggregateStorageShapeDriftTest.EXPECTED_PERIOD_STORED`
(step 5).

## 4. Calculator

Implement `MetricCalculator` in `metrics/calc`, annotated `@Component`, returning the new
type (or types, for a multi-metric calculator) from `produces()`. Persist through
`MetricSnapshotWriter.save(...)` only (never write `metric_snapshots` directly — see the
project's non-negotiable rule on saving metrics).

- DAILY shape: `periodFrom`/`periodTo` left null.
- AGGREGATE shape: `periodFrom`/`periodTo` set to the window the calculator actually
  computed over.

`MetricCalculatorRegistry` auto-discovers every `MetricCalculator` bean and validates at
startup that each `MetricType` is produced by exactly one calculator.

**Coupling:** the calculator's registration and its type coverage.
**Caught at startup** (`MetricCalculatorRegistry`'s constructor throws
`IllegalStateException` naming the uncovered type, or the type claimed twice) — this fires on
every application boot, not only in a test. **Caught by test** independently:
`MetricCalculatorRegistryTest` proves the validation logic with stubs, and
`AggregateStorageShapeDriftTest` proves it against the real, wired calculators — including,
as of R-NF-14, a classpath scan of `metrics.calc`
(`buildRegistry_includesEveryMetricCalculatorImplementation`) that fails if a new
`MetricCalculator` class exists but was not added to that test's hand-written
`buildRegistry()` list, so a new calculator cannot go silently unexercised by the shape-drift
checks below.

## 5. Reduction entry — AGGREGATE-shape types only

If the calculator writes `periodFrom`/`periodTo` (AGGREGATE shape), add an entry to
`AggregateWindowResolver.REDUCTIONS` naming how several stored windows (or several
repositories sharing one window) combine into one figure: `MEDIAN`, `MEAN`, `SUM`, or
`WIDEST_WINDOW`. Storage shape is a property of the calculator, **not** of
`MetricType.aggregatePeriod` — the AGGREGATE/DAILY split is 13/8 of the 21 types, while
`aggregatePeriod` is true for only 5 of the 13 AGGREGATE types (the ones recomputed on the
ISO-week grain). Do not use the flag as a proxy for "needs a reduction."

**Coupling:** the `REDUCTIONS` map entry, and the checked-in `EXPECTED_PERIOD_STORED` set in
`AggregateStorageShapeDriftTest`.

- If the new type also has `aggregatePeriod = true`: **caught at startup**
  (`AggregateWindowResolver`'s constructor validates every `aggregatePeriod` type has a
  `Reduction`, per R-NF-14 block 1a) in addition to the test coverage below.
- For all 13 AGGREGATE types regardless of `aggregatePeriod`: **caught by test** —
  `AggregateStorageShapeDriftTest.everyCalculator_periodStoredTypes_matchTheCheckedInSet` and
  `.periodStoredTypes_allHaveAReductionDeclared` both fail until `EXPECTED_PERIOD_STORED` and
  `REDUCTIONS` are updated together.
- At runtime, if a gap reaches production anyway: `AggregateWindowResolver.perWindow`/
  `.resolve` throw `IllegalStateException` naming the type rather than silently defaulting to
  `MEDIAN` (R-NF-14 block 1b) — a loud 500, not a wrong number, but this is a **last resort**,
  not a substitute for the two checks above.

## 6. Context list — AI-context types only

If `inAiContext = true`, add the constant to **both**
`AiContextBuilderService.CONTEXT_METRIC_TYPES` and
`MetricsAnomalyService.CONTEXT_METRIC_TYPES`. Both are hand-written, fixed-order lists (the
presentation order the model reads is a decision, not derivable from the enum), so neither
follows the flag automatically.

**Coupling:** two independent list literals.
**Caught by test**: `AiContextBuilderServiceTest.contextMetricTypes_matchesInAiContextFlag_inBothDirections`
and `MetricsAnomalyServiceContextTypesTest.contextMetricTypes_matchesInAiContextFlag_inBothDirections`
each pin their list against `MetricType.inAiContext` in both directions, so forgetting either
list, or adding a type to one but not the other, fails one of these two tests. Neither test
adds the missing entry for you.

If the metric is central to a thesis research question, also read
`MetricsAiService.generateSummary`'s "New context metric types" note (per the project's
"When touching the AI layer" rule) — the prompt must be told about the type, or the model
never mentions data it was silently given.

## 7. Read endpoint

Add a `@GetMapping` to `MetricsController` (personal scope) and, if the metric is
team-relevant, `MetricsTeamController`. Reuse `MetricSnapshotService`'s existing
parameterized query methods; add a new one only if the read shape genuinely differs.
`@PreAuthorize` is class-level on both controllers — do not repeat it per method unless the
route's rule differs from the class default (the project's RBAC non-negotiable rule).

**Coupling:** the endpoint itself. **Not caught** by any drift guard — there is no test that
enumerates `MetricType.values()` and asserts a matching route exists. This step depends
entirely on the three required test layers (unit, integration, controller/slice) being
written for the new endpoint, per the project's testing rules, and on code review. A missing
endpoint's only indirect signal is the `datasource.controller`/`metrics.controller` package
coverage gate (Block 2) dropping if the new route ships untested — that gate does not detect
a route that was never added at all.

## 8. Tests

All three layers, per the project's testing rules:

- **Unit** — the calculator's branch paths (`@ExtendWith(MockitoExtension.class)`).
- **Integration** — the repository query and, if the type is AGGREGATE-shape, that
  `AggregateWindowResolver` resolves it correctly (`AggregateWindowResolverTest` has one test
  per `Reduction` kind — extend it if a genuinely new combination rule is needed instead of
  reusing an existing one).
- **Controller/slice** — the new endpoint's status codes, `@PreAuthorize` enforcement, DTO
  shape (`@WebMvcTest` + `@WithMockUser`).

Also update, if the new type touches what they check:

- `MetricTypeTest` — the three exact-count assertions named in step 2.
- `MetricSnapshotTableCommentTest` — no edit needed; it reads the comment migration directly
  (step 3), but it will fail if that migration is missing or the type lands in the wrong
  section.
- `AggregateStorageShapeDriftTest` — `EXPECTED_PERIOD_STORED` and `buildRegistry()` (step 4
  and 5).
- `MetricCalculatorCharacterisationTest` — add the new calculator to the hand-written list in
  `buildRegistry()` (step 4). Unlike `AggregateStorageShapeDriftTest.buildRegistry()`, this
  second, independent list has no classpath-scan self-check — a forgotten calculator fails
  only `registry_coversEveryMetricType`, not a drift guard that names the missing class.
- `AiContextBuilderServiceTest` / `MetricsAnomalyServiceContextTypesTest` — no edit needed;
  they read the flag directly (step 6), but they will fail if the context list edit is
  missing.

## 9. Frontend

`frontend/src/api/metrics.ts` (typed fetcher), `frontend/src/types/index.ts` if a new DTO
shape is needed, and a chart/card on `DashboardPage.tsx` (or the team dashboard). Run
`npm run build` and commit the rebuilt `static/` bundle — Maven does not build the frontend.

**Coupling:** none checked by the backend test suite. **Not caught** by anything in this
repository; a metric can ship with a working API and no UI indefinitely. Confirm in the
browser before calling the metric done, per the project's UI verification rule.

## 10. Thesis cross-check

`docs/metrics/<slug>.md`'s wording must match the controller Javadoc and any UI label
verbatim (the project's thesis-code consistency rule). Manually verify the total metric
count against the thesis appendix table — this repository does not pin
`MetricType.values().length` to a fixed number (R-NF-14 removed
`MetricTypeTest.totalMetricCount_isTwentyOne`, since a raw count test needs editing on every
addition and adds no invariant beyond what steps 2–8 already enforce more specifically).
**Not caught** by any test; this is the one step with no automated backstop at all — treat it
as a required manual checklist item, not optional.

---

## Coupling summary

| Coupling | Omission caught |
|---|---|
| `MetricType` constant (unit, flag subset rules) | test |
| `MetricType` constant (exact-count assertions) | test (manual fix required) |
| `metric_snapshots` table comment (every type listed) | caught (test) — `MetricSnapshotTableCommentTest` |
| Calculator registration and type coverage | startup + test |
| Calculator added to `MetricCalculatorCharacterisationTest`'s list | caught (test) — `MetricCalculatorCharacterisationTest` |
| Reduction entry, `aggregatePeriod = true` type | startup + test |
| Reduction entry, other AGGREGATE type | test (+ runtime `IllegalStateException` as last resort) |
| Context list (both declarations) | test |
| Read endpoint | not caught |
| Frontend wiring | not caught |
| Total metric count vs. thesis appendix | not caught |
