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
