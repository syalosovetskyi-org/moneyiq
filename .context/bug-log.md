# Review bug log

Date: 2026-11-04. Shell: Linux Bash.

Verification command for all entries:
`cd onemoney && bash gradlew :app:testDebugUnitTest --console=plain`.
Execution blocked before test discovery by Gradle wrapper network access:
`java.net.SocketException: Operation not permitted`. No JVM results, compilation
results, or pre-fix red / post-fix green results are claimed.

| Symptom | Root cause | Fix | Files changed |
| --- | --- | --- | --- |
| Restored foreign transfer reverses 820 USD instead of 20 USD | JSON omits toAmount | Preserve nullable credited amount on serialize/deserialize | BackupSerializer.kt, BackupSerializerTest.kt |
| Changing transfer source retains credit in old source currency | keepsCredit ignores accountId | Include source account in credit cache key | TransactionRepository.kt, TransactionRepositoryTest.kt |
| Repeated foreign transfer/repayment overcredits destination | Worker uses amount for both sides | Use toAmount fallback, expose balance helper for JVM tests; warn on retry and propagate cancellation | RepeatTransactionWorker.kt, RepeatTransactionBalanceTest.kt |
| Month report omits boundary transactions | Current millisecond carried into month boundaries | Set start to .000 and end to .999 | ReportsViewModel.kt, ReportsViewModelTest.kt |
| DST range day count off by one | Milliseconds divided by fixed 24h | Count local calendar dates | SelectedMonthRepository.kt, SelectedMonthRepositoryTest.kt |
| Decimal-comma CSV has extra column | Localized amount contains CSV separator | Format amount with Locale.ROOT | CsvExporter.kt, CsvExporterTest.kt |
| Sync account update deletes existing transaction history | INSERT OR REPLACE deletes FK parent, invoking CASCADE/SET NULL | Upsert account rows without deletion | AccountDao.kt, ParentUpsertTest.kt |
| Sync category update clears historical category assignments | REPLACE invokes ON DELETE SET NULL | Upsert categories without deletion | CategoryDao.kt, ParentUpsertTest.kt |

Independent SQLite/zoneinfo reproduction confirmed both REPLACE side effects and
spring/autumn DST count errors. This does not substitute for the JVM regression suite.

## Zerno follow-up #2673 — 2026-11-04

All paths below are under `onemoney/app/src/main/java/org/pixelrush/moneyiq/`.
Verification for Kotlin changes: `cd onemoney && bash ./gradlew --offline :app:testDebugUnitTest :app:assembleDebug`.
**Not executed successfully:** wrapper lock is read-only; the workspace Gradle home
starts Gradle but fails before compilation with `Could not determine a usable wildcard IP for this machine`.
Regression tests were written before the first implementation edits where noted in the
report; actual red/green execution is unavailable. No Kotlin fix is claimed verified.

| Symptom | Root cause | Fix / files | Regression / remaining limit |
|---|---|---|---|
| Partial/corrupt local backup; SQLite WAL missing | Copying the live DB file directly to final name | `util/AtomicBackupFile.kt`, `data/repository/BackupStore.kt`, `ui/data/DataViewModel.kt`: transactional JSON snapshot, temporary file, fsync, atomic move | `AtomicBackupFileTest`, `BackupAndRepeatTransactionTest`; JVM not run |
| Drive backup deletes prior copy before completing new one | Delete-then-write; null output stream treated as success | `workers/DriveBackupWorker.kt`, `ui/data/DataViewModel.kt`: unique timestamp, staging document, close then rename, cleanup/log; explicit error for null streams | `WorkerBehaviorTest` covers no-folder/failure only; real SAF publication still untested |
| Invalid restore erases current data | Independent destructive DAO calls before FK check | `util/BackupSerializer.kt`, `data/repository/BackupStore.kt`, `ui/data/DataViewModel.kt`: validation then Room transaction | `BackupValidationTest`, fault injection in `BackupAndRepeatTransactionTest` |
| Calculator crashes on overflow; NaN reaches save | `toBigDecimal()` on Infinity; <=0 does not reject NaN | `ui/components/calculator/CalcState.kt`, `ui/transactions/TransactionViewModel.kt`: reject nonfinite values, log calculator rejection, accept digit keys only | `CalcStateTest`, `TransactionViewModelTest` |
| Weekly budget treated as monthly | budgetPeriod ignored | `ui/budget/BudgetViewModel.kt`, `ui/budget/BudgetScreen.kt`: selected range allowance in separate field; edit keeps stored limit | `CurrencyAndFormatTest`; semantics ADR-076 |
| Repeated operation duplicated after interruption | Insert, balance updates, due marker committed separately | `workers/RepeatTransactionWorker.kt`: re-read and consume marker under one Room transaction | stale claim + SQL-failure rollback in `BackupAndRepeatTransactionTest` |
| Editing note disables recurrence | Reconstructing entity with default recurrence fields | `ui/transactions/TransactionViewModel.kt`: original entity copy preserves recurrence, createdAt, toAmount | `TransactionViewModelTest` |
| Currency totals mix UAH/USD | Raw sums across unlike units | `util/CurrencyTotals.kt`, `ui/accounts/AccountsScreen.kt`, `ui/transactions/TransactionsListScreen.kt`: grouped finance and filtered summary | `CurrencyAndFormatTest`; reports/overview/budget/other aggregates remain OPEN |
| Currency rounding 999.999 becomes 999.99 | Fraction rounded separately and clamped | `ui/settings/data/SettingsData.kt`: BigDecimal rounding before splitting; `util/MoneyFormat.kt`: uk-UA, symbol resolution, minus sign | `CurrencyAndFormatTest`; full settings-format propagation remains open |
| Budget deleted immediately | Destructive callback wired without dialog | `ui/budget/BudgetScreen.kt`, translated resources: explicit confirmation | `DestructiveAndNavigationTest` |
| Dead drawer destinations | No-op callbacks for sign-in, rate, support, about/search | `ui/main/MainScreen.kt`: keep settings/data destinations, remove no-ops; remove unreachable legacy `ui/more/MoreScreen.kt` with raw DB restore | `DestructiveAndNavigationTest`; complete shell navigation not tested |
| Form input lost on rotation | remember instead of rememberSaveable; init effect overwrites restored calculator | Account/category forms, QuickExpenseSheet, icon picker, budget currency, TransactionDetailSheet, CalcState use saveable fields/IDs | `FormRestorationTest`; parent sheet selection and transfer form remain open |
| Dark money colors have insufficient contrast | Light-only semantic constants used by UI | `ui/theme/Theme.kt`, `OneMoneyTokens.kt`, money UI: supplied light/dark tokens via existing CompositionLocal | `ZernoColorTest`; independent contrast calculation passes, visual audit not run |
| Exceptions disappear without diagnostics | Empty catch or runCatching fallback | Worker, VM, color parser, settings, rate cache: warning/error logging; cancellation propagated in updated coroutine paths | Static inspection only; no runtime logging validation |

## Personal emulator regressions and local commands — 2026-11-08 (#2673)

Shell: Linux Bash. Gradle, Kotlin compilation and tests **not run**, as requested.
Verification to run outside this environment:
`cd onemoney && ./gradlew :app:testPersonalDebugUnitTest :app:testPlayDebugUnitTest :app:assemblePersonalDebug :app:assemblePlayDebug`.

| Symptom | Root cause | Fix / files | Regression |
|---|---|---|---|
| Fresh Wallet was RUB with Russian name | Hardcoded initial seed | MoneyIQApp.kt + localized strings: UAH/localized default only on empty accounts | InitialAccountTest |
| Total/Overview 0 despite negative foreign balance; category/ring showed original amount as UAH | Missing rates silently zeroed in AccountRepository, DAO aggregates mixed native amounts | BaseValuation.kt, AccountRepository.kt, TransactionRepository.kt, CurrencyStatusViewModel.kt, MainScreen.kt: convert before sums, explicit partial status; pickers/widget show separate units | PersonalCurrencyRegressionTest bugs 2/3/5 |
| Opening 200, Closing 0 after first 200 expense | Current balance in UAH combined with unconverted period movement; historical periods also used current balance | BaseValuation.periodBalances + TransactionsListViewModel: reconstruct both boundaries including transfers/later rows | PersonalCurrencyRegressionTest bug 4 and historical/excluded-transfer cases |
| Forecast displayed without income/budget | hasForecast checked only elapsed days and spending | BudgetScreen.kt + canForecast: require income or income budget | PersonalCurrencyRegressionTest bug 6 |
| Play exposed personal sync; unconfigured worker succeeded | Credentials/worker/UI gates belonged to shared debug configuration | Flavor BuildConfig/defaults, MonoFlowPolicy, workers, app schedule reconciliation, Data UI: personal only, explicit not_configured failure | Both MonoFlowFlavorTest variants, WorkerBehaviorTest |
| API add/undo must not mutate manual rows or partially update balances | New local command boundary needs caller trust and durable ownership | personal/alisaphone service/verifier/commands, personal manifest, signed API IDs + backup validation | CallerVerifierTest, AndroidCallerVerifierTest, ApiRequestTest, AlisaphoneCommandsTest including Room rollback and backup round trip |


## 2026-11-11 — Categories redesign (#2673; awaiting CI)

- Symptom: category Add below fold and weak contrast, clipped final row, four-column
  layout on Fold, tiny labels, type switch discoverable only by tapping the ring.
- Root cause: standalone Add tail row, fixed width/height assumptions, pager viewport
  included bottom navigation, pastel circles and 9–11sp typography.
- Fix: row-major 4/6-column measured layout, inline Add cell, explicit segment and
  transaction FAB, Scaffold padding on shell, approved light/dark palette and
  luminance-normalized category colors. Removed weighted chip spacers so minimum
  row heights can safely grow with text. Existing seven-argument onSave preserved.
- Files: CategoriesScreen.kt, CategoriesWidgets.kt, CategoryControls.kt, MainScreen.kt,
  SharedMonthPill.kt, CategoryScreenTokens.kt, OneMoneyTokens.kt, Theme.kt, strings.
- Verification: `git diff --check`, static XML/palette validation only. CI must run
  `:app:compilePersonalDebugKotlin :app:testPersonalDebugUnitTest` and Compose tests.
  Gradle, Kotlin compilation and Android tests were not run in this session.
