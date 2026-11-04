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
