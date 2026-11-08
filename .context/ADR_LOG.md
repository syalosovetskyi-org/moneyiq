# ADR Log

## ADR-001: Native Android + Compose

The app is implemented as a native Android app using Kotlin and Jetpack Compose. Continue feature work in Compose unless a platform API requires XML or manifest-level configuration.

## ADR-002: Local-First Persistence

Room is the source of truth for accounts, categories, and transactions. Data access should go through DAOs and repositories rather than ad hoc storage.

## ADR-003: Repositories Own Balance Side Effects

`TransactionRepository` applies and rolls back balance changes when transactions are added, deleted, or updated. UI code must not independently adjust account balances.

## ADR-004: Categories Are Hierarchical

Categories support `parentId`; root categories drive the broad category view and children may be shown or aggregated depending on the screen state.

## ADR-005: 1Money-Like UX Is A Product Constraint

Several layouts intentionally mimic 1Money behavior, especially categories, month navigation, and bottom tabs. Preserve visual similarity when changing these surfaces.

## ADR-006: Context Docs Are Not Runtime Code

`.context` is documentation only. Do not import it, bundle it, or make runtime behavior depend on it.

## ADR-007: Shared Month State Via Repository

`SelectedMonthRepository` is the single source of truth for the selected period. All feature screens observe its `StateFlow<AppMonth>`. Do not give individual screens their own month state. Month navigation (prev/next/jump) must go through this repository.

## ADR-008: CategoryStyleUtil For Icon/Color Suggestion

`util/CategoryStyleUtil.kt` owns the keyword→icon/color mapping. When adding new categories programmatically (seeder, import), always use `suggestCategoryStyle(name, type)` if the icon key is unknown or invalid. Do not hard-code icon key strings outside this utility and `CategoryRepository.seedDefaults()`.

## ADR-009: Settings Are Overlaid, Not Navigated

`SettingsScreen` and `EditCategoriesScreen` are rendered as full-screen Compose overlays inside `MainScreen`, not as separate navigation destinations. This keeps the `NavGraph` minimal (only `Main` and `AddTx` routes). Do not add routes for settings or category editing.

## ADR-010: Shared UI Components Live In `ui/components/`

All reusable composables that are used across multiple feature packages must live in `ui/components/`, not inside a feature package. The `ui/components/` folder has four sub-packages:

| Sub-package | File | Contents |
|---|---|---|
| `calculator/` | `CalcState.kt`, `CalcKeypad.kt`, `CalcDateSheet.kt` | `CalcStateHolder`, `SharedCalcKeypad`, `AmountCalculatorSheet`, `CalcDateSheet`, `AccountPickerSheet` |
| `dialogs/` | `TextInputDialog.kt`, `ConfirmationDialog.kt` | Generic alert dialogs |
| `currency/` | `CurrencyPicker.kt` | `CurrencyPickerSheet` (Dialog-based), `CurrencyPageContent` (bare column) |
| `form/` | `FormComponents.kt` | `FormSectionHeader`, `FormNavRow`, `FormValueRow` |
| `icons/` | `IconBox.kt` | `CircleIconBox`, `RoundedIconBox` |

**`CurrencyPickerSheet`** — full-screen Dialog with 3 tabs (Main / Other / Crypto). Use when opening from a bottom sheet or form.

**`CurrencyPageContent`** — bare `Column(fillMaxSize)`. Caller wraps in `Dialog` or uses as a navigation page (SettingsScreen).

**`CircleIconBox(icon, color, boxSize=40dp, iconSize=20dp, tint=White, modifier)`** — icon in a colored circle. Default 40×20dp.

**`RoundedIconBox(icon, color, cornerRadius=12dp, boxSize=48dp, iconSize=26dp, tint=White, modifier)`** — icon in a rounded rectangle.

**Rule:** Do not define currency pickers, icon-in-circle boxes, or form row helpers inside feature packages. Import from `ui.components.*`. If a helper is used from only one file, keep it private in that file — move to components only when a second caller appears.

## ADR-011: ViewModels Live In Separate Files From Screens

Each screen file owns only composable functions. The `@HiltViewModel` class, `UiState` data class, and supporting data classes live in a dedicated `*ViewModel.kt` file in the same package. This applies to: `BudgetViewModel`, `OverviewViewModel`, `TransactionsListViewModel`, `DataViewModel`.

## ADR-012: Transaction Sheet Composables Split Into Dedicated Files

`TransactionSheets.kt` (deleted 2026-05-29) was split into four focused files in `ui.transactions`:

- `TxSearchScreen.kt` — `TxSearchScreen`, `SearchSectionHeader`, `TypeFilterCard`, `ColoredFilterChip`
- `CategoryPickerSheet.kt` — `CategoryPickerSheet`, `CategoryPickerCell`, `AccountPickerRow`
- `TransferQuickSheet.kt` — `TransferQuickSheet`
- `TransactionDetailSheet.kt` — `TransactionDetailSheet`

All composables use `internal` visibility (same module, same package). They must NOT be declared `private` — `TransactionsListScreen.kt` calls them across file boundaries within the package.

## ADR-018: Reusable Dialog Composables Live In `ui/components/dialogs`

`TextInputDialog` and `ConfirmationDialog` (added 2026-05-29) replace inline `AlertDialog` blocks that were copied across `CategoryFormSheets.kt`, `TransactionDetailSheet.kt`, and similar files.

- **`TextInputDialog`** — `title`, `label`, `initialValue`, `allowDismiss` (when `false`: OK enabled only after typing, Cancel hidden, back press blocked), `confirmText`, `dismissText`, `onConfirm(value)`, `onDismiss`. State lives inside the composable.
- **`ConfirmationDialog`** — `title`, `message`, `icon?` (drawn in error tint), `confirmText`, `dismissText`, `destructive` (error-colored TextButton when `true`), `onConfirm`, `onDismiss`.

Both are `internal` (module-visible). Import from `org.syalosovetskyi.onemoney.ui.components.dialogs`. Do not re-inline new copies of these patterns.

## ADR-019: Sentry For Crash Reporting

Sentry Android SDK (`sentry-android 7.20.0`) is initialized in `onemoneyApp.onCreate` before any coroutine work starts. The Sentry Gradle plugin (`io.sentry.android.gradle 4.14.1`) uploads ProGuard mappings and source context automatically on release builds.

- DSN is hardcoded in `onemoneyApp.kt` (not a secret — it is the public ingest endpoint).
- Auth token for the Gradle plugin lives in `local.properties` (gitignored) and is never committed. CI uses the `SENTRY_AUTH_TOKEN` env var.
- `buildConfig = true` is enabled in `app/build.gradle.kts` so `BuildConfig.DEBUG` and `BuildConfig.VERSION_NAME` are available.
- `environment` is set to `"debug"` or `"production"` based on `BuildConfig.DEBUG`.
- `release` is set to `"onemoney@${BuildConfig.VERSION_NAME}"`.
- Screenshots, view hierarchy, and user interaction tracing are enabled (`isAttachScreenshot`, `isAttachViewHierarchy`, `isEnableUserInteractionTracing`).
- `tracesSampleRate = 1.0` during development — reduce to `0.2` or lower before high-traffic release.

## ADR-013: Comprehensive Unit Test Suite Exists

The project has a full JUnit4/MockK/Turbine/Robolectric test suite (~196 tests). When adding or changing a repository, ViewModel, or utility class, add tests covering the changed paths. Follow existing patterns: `MainDispatcherRule`, `runTest`, Turbine `.test {}`. Testing dependencies are already configured in `libs.versions.toml` and `app/build.gradle.kts`.

## ADR-014: DataViewModel Injects DAOs Directly (Known Debt)

`DataViewModel` injects `TransactionDao`, `AccountDao`, `CategoryDao` directly rather than through repositories. This is a known gap: `// TODO: Migrate direct DAO access to repository layer`. Do not copy this pattern for new ViewModels.

## ADR-015: Same-Name Subcategory Deduplication In Category Expansion

When computing `expandedChildren` for the double-click expansion strip and inline panel, any child category whose `name.trim().lowercase()` equals the parent's is excluded. This prevents a subcategory "Продукти" from appearing inside its parent "Продукти".

The deduplication is applied at the **display layer only** (`CategoriesGridContent`) — the database retains the child record. It is intentional and silent; no warning is shown to the user.

If two semantically different categories genuinely need the same name under the same parent, they should be distinguished by icon/color rather than name.

## ADR-016: Specific Icon Keys For Root Categories

Root category icon keys must be semantically specific — not generic fallbacks. Correct keys (current, after migration 25):

| Category       | Icon key      | Material icon           | Color      |
|----------------|---------------|-------------------------|------------|
| Продукти       | `grocery`     | `ShoppingBasket`        | `#4AAFE8`  |
| Ресторація     | `restaurant`  | `Restaurant`            | `#4659BE`  |
| Дозвілля       | `theater`     | `TheaterComedy`         | `#F73579`  |
| Транспорт      | `bus`         | `DirectionsBus`         | `#FFA834`  |
| Здоров'я       | `volunteer`   | `HealthAndSafety`       | `#48B456`  |
| Подарунки      | `gift`        | `CardGiftcard`          | `#F34B4D`  |
| Сім'я          | `family`      | `FamilyRestroom`        | `#7A48F2`  |
| Покупки        | `shopping`    | `ShoppingCart`          | `#7B5947`  |
| Робота         | `work`        | `Work`                  | `#1565C0`  |
| Таксі          | `taxi`        | `LocalTaxi`             | `#FDD835`  |
| Авто           | `car`         | `DirectionsCar`         | `#FF7043`  |
| Паркінг        | `parking`     | `LocalParking`          | `#78909C`  |
| АЗС/Пальне     | `gas_station` | `LocalGasStation`       | `#FF8F00`  |
| Оренда         | `key`         | `Key`                   | `#9C27B0`  |
| Фриланс        | `laptop`      | `Laptop`                | `#26A69A`  |
| Аптека         | `pharmacy`    | `Medication`            | `#43A047`  |
| Лікар/Медицина | `doctor`      | `MedicalServices`       | `#D81B60`  |
| Спорт          | `sports`      | `DirectionsRun`         | `#F44336`  |
| Кіно (child)   | `movie`       | `Movie`                 | `#9C27B0`  |
| Gaming (child) | `gaming`      | `SportsEsports`         | `#607D8B`  |
| Зв'язок        | `phone`       | `PhoneAndroid`          | `#3F51B5`  |
| Інтернет       | `wifi`        | `Wifi`                  | `#00BCD4`  |
| Фінанси        | `money`       | `AttachMoney`           | `#F9A825`  |
| Комуналка      | `home`        | `Home`                  | `#546E7A`  |
| Food delivery  | `delivery`    | `LocalShipping`         | `#FF6F00`  |
| Ресторани      | `restaurant`  | `Restaurant`            | `#E53935`  |
| Кафе           | `coffee`      | `LocalCafe`             | `#795548`  |
| Штрафи         | `gavel`       | `Gavel`                 | `#BF360C`  |
| Проценти/ПДВ   | `percent`     | `Percent`               | `#F9A825`  |

**Icon distinctness rule** (migration 19→20, 2026-05-31): Every commonly-used subcategory has a distinct icon. Аптека→`pharmacy` (Medication pill), Лікар→`doctor` (MedicalServices cross), Спорт→`sports` (DirectionsRun — running figure). Паркінг→`parking` separate from Авто (`car`). Оренда→`key`. Фриланс→`laptop`.

**Volunteer icon** (2026-05-31): `volunteer` key uses `Icons.Outlined.HealthAndSafety` (shield with cross/heart — clearly "health & safety"). Changed from `FavoriteBorder` (generic heart) and before that from `VolunteerActivism`.

**Sports icon** (2026-05-31): `sports` key uses `Icons.Outlined.DirectionsRun` (running figure). Changed from `FitnessCenter` (dumbbell) to better distinguish from "gym" and to be more universally recognisable as physical activity.

**Transport icon split** (migration 17→18): `bus`/`DirectionsBus` for public transport vs `car`/`DirectionsCar` for personal vehicle.

Available leisure sub-icons: `theater` (Дозвілля), `movie` (Кіно), `gaming`, `celebration`, `spa`, `ticket`.

These are registered in `CategoryIcons.kt` (`CATEGORY_ICONS_LIST`) and mapped in `CategoryStyleUtil.kt` (`iconColorMap`). Data migrations 5→25 backfill existing DB rows. The old `health` key is kept as a legacy entry in `iconColorMap` only — do not use it for new categories or seeds.

Migration 12→13: Deletes EXPENSE categories named "Фінанс*".
Migration 13→14: Root category color palette refresh.
Migration 14→15: Subcategory icon fix (delivery/coffee/restaurant, parentId guard, TRIM+LIKE).
Migration 15→16: `money` icon safety net for remaining "Фінанс*".
Migration 16→17: Subcategory icons via exact name matching.
Migration 17→18: `bus` for root Транспорт.
Migration 18→19: Fixes placeholder `category` icons for money/celebration/theater.
Migration 19→20: Distinct icons for pharmacy/doctor/parking/gas_station/key/laptop/sports.
Migration 20→21: Structural — adds `currencyCode TEXT NOT NULL DEFAULT 'UAH'` to categories.
Migration 21→22: Bulk icon fixes for existing rows: delivery/clothes/school/devices/doctor/sports/parking/percent/gavel.
Migration 22→23: Utilities icons: home/phone/wifi.
Migration 23→24: Здоров'я root `volunteer`/`#48B456`; Спорт→`sports`; remaining `health` roots→`volunteer`.
Migration 24→25: **Unconditional** fix for Дозвілля (`theater`/`#F73579`) and Розваги (`celebration`/`#FF6D00`) — no `icon = 'category'` guard because imported data via REPLACE strategy can silently overwrite prior migration results.

## ADR-017: Large Screen Files Split Into Companion Files

Screen files that exceeded ~600 lines were split into a main file + companion file(s) in the same package (2026-05-29). Each companion holds composables that the main screen delegates to but that do not need to be top-level nav destinations.

Current companion file map:

| Package | Main file | Companion file(s) |
|---|---|---|
| `ui.accounts` | `AccountSheets.kt` | `AccountPickerSheets.kt` |
| `ui.budget` | `BudgetScreen.kt` | `BudgetSheets.kt` |
| `ui.categories` | `CategoriesScreen.kt` | `CategoriesWidgets.kt` |
| `ui.categories` | `CategorySheets.kt` | `CategoryFormSheets.kt` |
| `ui.categories` | `CategoriesScreen.kt` | `EditCategoriesScreen.kt` |
| `ui.data` | `DataScreen.kt` | `DataWidgets.kt` |
| `ui.overview` | `OverviewScreen.kt` | `OverviewSheets.kt` |
| `ui.settings` | `SettingsScreen.kt` | `SettingsSubScreens.kt` |
| `ui.transactions` | `TransactionsListScreen.kt` | `TxSearchScreen.kt`, `CategoryPickerSheet.kt`, `TransferQuickSheet.kt`, `TransactionDetailSheet.kt` |

**Rule:** Composables shared across files in the same package must be `internal`, not `private`. `private` is reserved for helpers used exclusively within the same file. Constants (`val`) shared across companion files must also be `internal`.

## ADR-020: Icon/Color Fixes For Existing Categories Go In Room Migrations

`CategoryStyleUtil` auto-suggest and `CategoryRepository.seedDefaults()` only affect NEW or freshly installed categories. To fix icon/color on categories already stored in the user's DB, add a `MIGRATION_N_(N+1)` in `AppDatabase.kt` with a targeted SQL `UPDATE`.

Pattern:
```kotlin
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("UPDATE categories SET icon = 'home', colorHex = '#546E7A' WHERE name IN ('Комунальні', 'Комуналка')")
    }
}
```

Also update: DB `version` in `@Database`, `ALL_MIGRATIONS` array in `AppDatabase.kt`, seed in `CategoryRepository`, auto-suggest rule in `CategoryStyleUtil`, and the icon table in `UI_CONTRACTS.md`.

**REPLACE strategy risk:** `insertCategories` uses `@Insert(onConflict = OnConflictStrategy.REPLACE)`. Importing a backup or syncing data with a wrong icon silently overwrites correctly migrated icons — subsequent app launches do NOT re-run already-completed migrations.

**Generic icon guard:** `normalizeImportedCategory()` in `DataViewModel` and the normalization step in `MonoFlowSyncWorker` now treat `"category"` and `"family"` as generic placeholders. For any imported category whose icon is in that set, `suggestCategoryStyle(name, type)` is called; if the result is more specific than `"category"`, the icon and color are overridden before insert. This means: a category named "Зв'язок" arriving with `icon="family"` is automatically corrected to `icon="phone"` at import time.

**Rule for import-resilient fixes:** When a named category could be re-imported with a wrong icon, **omit the `AND icon = 'category'` guard**. Use an unconditional `UPDATE ... WHERE LOWER(TRIM(name)) = 'xxx'` (see migration 24→25: Дозвілля/Розваги). The generic-icon normalization in `normalizeImportedCategory()` handles the `"family"` case for future imports without a new migration.

## ADR-021: LazyColumn Spacing Via `verticalArrangement`, Not Item Padding

Never use `Modifier.height(N).padding(bottom = K)` on chip row items inside a `LazyColumn`. In Compose, `padding` inside a `height` constraint subtracts from the content area — 12dp of bottom padding on a 136dp row leaves only 124dp for content, clipping the spending text at the bottom of each chip.

**Rule:** Use `LazyColumn(verticalArrangement = Arrangement.spacedBy(CATEGORY_VERTICAL_GAP))` for inter-row spacing. Individual row items use only `Modifier.height(chipHeight)` — no bottom padding.

This applies to `CategoriesGridContent` and any future screen that places fixed-height chip rows in a `LazyColumn`.

## ADR-022: Category Filter In Transactions Includes Children

When a root category is selected as a filter in `TransactionsListScreen`, the filter expands to include all direct child categories. This means selecting "Ресторація" also shows transactions from "Glovo", "Ресторани", "Кафе".

Implementation in `filteredTransactions` inside `TransactionsListScreen.kt`:
```kotlin
val allCats = state.expenseCategories + state.incomeCategories
val expandedCatIds = if (filterCategoryIds.isEmpty()) emptySet<Long>() else {
    filterCategoryIds + allCats.filter { it.parentId in filterCategoryIds }.map { it.id }.toSet()
}
```
`remember` keys include `state.expenseCategories` and `state.incomeCategories` to recompute when categories change.

**Rule:** Do not change this to a DAO-level filter without ensuring child IDs are also fetched. The expansion is one level deep (grandchildren are not included).

## ADR-023: No `graphicsLayer { clip = false }` On Dialog Siblings

`graphicsLayer { clip = false }` forces a hardware layer on the composable. Android composites such layers above `Dialog` windows regardless of Compose z-ordering — any composable using this modifier will render visually above `CategoryActionSheet` or similar dialogs, breaking the scrim.

**Rule:** Do not apply `graphicsLayer { clip = false }` (or any `graphicsLayer { ... }` block that sets `clip = false`) to composables that are siblings of, or ancestors of composables that launch, `Dialog` calls. If content genuinely needs to overflow its clip boundary alongside dialogs, place it in a full-screen root-level overlay `Box` instead.

## ADR-024: AddCategoryChip Is Inside DonutChart

The `+` (add category) chip is placed **inside** the `DonutChart` composable, positioned at `Alignment.BottomCenter` with `padding(bottom=28.dp)`. It is NOT a standalone `LazyColumn` item.

`DonutChart` signature: `onAdd: (() -> Unit)? = null` — when non-null, the chip is rendered inside the chart's `Box` overlay.

Current dimensions: `Column(width=64dp)`, circle `Box(44dp)`, icon `18dp`.

`DONUT_SECTION_HEIGHT = CHIP_HEIGHT * 2 + CATEGORY_VERTICAL_GAP` ensures the donut + chip fit exactly in the space flanked by 2 mid-column chips on each side.

## ADR-026: BudgetViewModel `totalAmount` Counts All Spending, Not Just Budgeted

`BudgetSectionData.totalAmount` is the sum of **all** actual spending for the period (`expRows.sumOf { it.amount }`), regardless of whether a category has a budget set.

Previously this was filtered to `expRows.filter { it.category.budgetAmount > 0 }.sumOf { ... }`, which caused "витрачено 0.00" on the Budget screen whenever no category had a budget — even with real transactions.

`totalAmount` drives:
- "витрачено N" label in `BudgetSectionCard`
- `expenseTotal` passed to `IncomeBudgetBar` for "Доступно в бюджеті" calculation

**Rule:** Do not re-add the `budgetAmount > 0` filter. The "в бюджеті" figure uses `totalBudget` (sum of budget amounts); the "витрачено" figure uses `totalAmount` (sum of actual spending). These are intentionally separate.

## ADR-027: MonoFlow Sync Uses BackupSerializer JSON, Not CSV

`MonoFlowSyncWorker` calls `GET $url/api/sync?since=$lastSyncMs` (Bearer token auth) and deserializes the response with `BackupSerializer.deserialize(json)`. The `/export/flow.csv` endpoint is a separate export for the Flow app and is not consumed by onemoney.

The JSON format is the same as the manual backup:
```json
{
  "version": 1, "exportDate": ..., "app": "onemoney",
  "accounts":     [ { "id", "name", "type", "balance", "currency", ... } ],
  "categories":   [ { "id", "name", "type", "colorHex", "icon", "budgetAmount", "parentId", ... } ],
  "transactions": [ { "id", "type", "amount", "accountId", "toAccountId", "categoryId", "note", "date" } ]
}
```

`type` on transactions is `EXPENSE | INCOME | TRANSFER | BORROW | LEND | REPAY`. `toAccountId` is a valid `Long` for transfers, `null` otherwise. The server is responsible for detecting PayPal/Revolut/ATM rows and setting `type=TRANSFER` with the appropriate `toAccountId`.

Worker normalizes categories before insert: for each category whose icon is `"category"` or `"family"`, runs `suggestCategoryStyle(name, type)` and replaces the icon/color if the result is more specific. Then inserts via `insertAccounts` / `insertCategories` / `insertTransactions` (REPLACE strategy — MERGE by `id`). Existing data is not deleted.

## ADR-028: Expanded Subcategory Strip Is Fused With Its Parent Row (2026-05-31)

`ExpandedCategoryStrip` (double-click expansion) is always rendered **in the same LazyColumn item** as the row that contains the expanded chip. The chip row and its strip are wrapped in a `Column` inside one `item { }` block — this eliminates the 8dp `Arrangement.spacedBy` gap that would otherwise appear between them.

`ExpandedCategoryStrip` has an `inline: Boolean = false` parameter:
- `inline = true`: no Card wrapper; thin `HorizontalDivider` (parent color 18% alpha) + plain `Column` with parent color 7% alpha background — appears flush with the chip row above
- `inline = false` (legacy / non-fused): wrapped in a `Card(RoundedCornerShape(16dp))`

The expanded chip uses `flatBottom = true` on `CategoryChip` (bottom corners 0dp instead of 12dp) so the chip and strip form one seamless visual block.

**Rule:** All three strip insertion points (`topRow`, `mid_section`, `extCats`) use `inline = true`. Do not add a separate `LazyColumn` item for the strip — doing so reintroduces the gap.

## ADR-029: Budget Savings Forecast Uses Linear Day Extrapolation (2026-05-31)

`BudgetViewModel` exposes `daysPassed: Int` in `BudgetUiState`. `SavingsSectionCard` uses it to project savings:

```
projectedExpenses = actualExpenses / daysPassed * daysInMonth
projectedSavings  = incomeBudget - projectedExpenses
```

Forecast is shown only when `daysPassed > 0 && daysInMonth > daysPassed && expenseTotal > 0` (i.e., current month with some spend data). Past/future months show only actual savings. This is intentionally simple linear extrapolation — do not replace with complex models without a product decision.

## ADR-030: EditCategoriesScreen Reuses CategoriesGridContent Without Badges (2026-05-31)

`EditCategoriesScreen` lives in **`EditCategoriesScreen.kt`** (its own file, not `CategoryFormSheets.kt`). It is the full-screen "Редагувати категорії" overlay and reuses `CategoriesGridContent` directly.

Key constraints:
- `childCounts = emptyMap()` — suppresses all +N subcategory badges without requiring a new parameter on `CategoriesGridContent`.
- Chip tap and long-press both open `CategoryFormSheet` for editing (not `QuickExpenseSheet`).
- The "Субкатегорії" `TextButton` in the top bar toggles `showSubcategories` state, forwarded to `CategoriesGridContent` exactly as in the main screen.
- The screen receives all data as parameters from `MainScreen` (no own ViewModel); mutations go through callbacks.

File: `ui/categories/EditCategoriesScreen.kt`.

## ADR-031: Budget Savings Header Shows Budget-Based Savings, Not Cash-Flow Savings

`SavingsSectionCard` header shows `effectiveIncomeBudget − expenseTotal` (when a budget is set), not `incomeTotal − expenseTotal`.

**Reason:** `incomeTotal` is often 0 for most of the month (salary arrives once), making `incomeTotal − expenseTotal` a large misleading negative. `effectiveIncomeBudget − expenseTotal` ("how much of your planned income remains after expenses") is the number users expect to see — it matches the "Доступно в бюджеті" bar at the bottom.

`effectiveIncomeBudget = incomeSection.totalBudget` — sum of per-category income `budgetAmount` values, computed in `BudgetScreen`, passed to both `SavingsSectionCard` and `IncomeBudgetBar` so they always agree.

**Internal name split:**
- `realSavings = incomeTotal − expenseTotal` — used only for the "збережено" subtitle label, and only when `incomeTotal > 0`
- `actualSavings = if (incomeBudget > 0) incomeBudget − expenseTotal else realSavings` — drives the header

**Rule:** Do not revert to `incomeTotal − expenseTotal` as the header without also replacing the bottom bar "Доступно в бюджеті". They must use the same budget source. Do not pass different income budget values to `SavingsSectionCard` and `IncomeBudgetBar` — both must receive `effectiveIncomeBudget`.

## ADR-032: CategoryFormSheet Title And Subcategory Section Are Context-Sensitive

`CategoryFormSheet` title adapts to category kind:

| Condition | Title |
|---|---|
| `existing == null && forParentId != null` | "Нова субкатегорія" |
| `existing == null` | "Нова категорія" |
| `existing.parentId != null` | "Субкатегорія" |
| `existing.parentId == null` | "Категорія" |

"Підкатегорії" section is rendered **only for root categories** (`existing != null && existing.parentId == null`). It shows existing children as icon rows and a "Додати підкатегорію" button (only when `onAddSubcategory != null`). Child categories never show this section.

New subcategory creation flow: caller sets `addSubcategoryTo = parent`, opens a new `CategoryFormSheet(forParentId = parent.id)`, on save calls `viewModel.add(..., parentId = parent.id)`. Single-parent uniqueness is enforced at the DB level by the `parentId` field — no UI-level deduplication needed.

**Rule:** Do not add nested subcategory levels (subcategory of a subcategory). One level of hierarchy is the product constraint.

## ADR-036: Income Budget Is Per-Category, Declared Via BudgetInputSheet (2026-05-31)

The "Введіть суму очікуваного доходу" bottom bar opens a **per-category income budget** flow, not a global budget stored in `SettingsRepository`.

**Rationale:** Income budgets are declarative intentions ("I expect to receive 10 000 salary this month"). There is no account involved — money has not arrived yet, so no account balance should be touched. Using `CategoryEntity.budgetAmount` for income categories is the same mechanism already used for expense budgets, keeping the model uniform.

**Entry-point routing** (in `BudgetScreen.kt`):
- If income has exactly **1 category** → open `BudgetInputSheet` directly for that category.
- If income has **2+ categories** → open `IncomeCategoryPickerSheet` (list of income categories) → user picks one → `BudgetInputSheet` opens for the chosen category.

**`IncomeCategoryPickerSheet`** (`BudgetSheets.kt`): lists `state.incomeSection.rows` with category icon, name, "отримано N ₴" subtitle, and either the current budget amount or an `Add` icon as trailing.

**`BudgetInputSheet`** is shared between expense and income categories. For income, `amountLabel = "отримано"` is passed so the subtitle reads "отримано N ₴" instead of "витрачено N ₴".

**Storage:** `BudgetViewModel.updateCategoryBudget(category, newBudget, currency)` calls `categoryRepo.update(category.copy(budgetAmount = newBudget, currencyCode = currency))`. No `SettingsRepository` is involved.

**`effectiveIncomeBudget`:** `state.incomeSection.totalBudget` — sum of `budgetAmount` across all income categories. No global fallback.

**Rule:** Do not restore a global income budget in `SettingsRepository`. Both `IncomeBudgetBar` and `SavingsSectionCard` derive the income budget from `incomeSection.totalBudget` — no account is needed.

**Rule:** Do not add account selection to `BudgetInputSheet` or `IncomeCategoryPickerSheet`. Declaring an income budget is not a transaction — it does not affect account balances.

## ADR-025: Overview List Falls Back To Transactions When No Categories

`OverviewScreen` renders the list below the stats row with this priority:

1. Category rows (spending > 0 for mode + period)
2. Transaction rows via `TransactionListItem` (when categories list is empty but transactions exist)
3. "Немає категорій" empty state (both empty)

**Reason:** Income/expense header totals are computed from all transactions of that type, regardless of category assignment. Without the fallback, a non-zero total header with an empty list is contradictory and confusing to the user.

**`OverviewUiState`** carries `transactions: List<TransactionWithDetails>` populated from `monoTx` (mode-filtered transactions for the period).

**Rule:** Do not remove the transaction fallback without also fixing the header total to exclude uncategorised transactions. Tapping a fallback transaction row is intentionally a no-op — if navigation to `TransactionDetailSheet` is added later, ensure the CategoryDetailSheet bottom sheet state is not inadvertently triggered.

## ADR-033: DonutChart Switches To Subcategory View When Parent Is Expanded (2026-05-31)

When a root category with children is double-tapped (subcategory expansion strip is shown), the `DonutChart` in the mid-section must show only the **subcategory spending breakdown** instead of the full category list.

**Implementation** (in `CategoriesGridContent`, just before the DonutChart call):

```kotlin
val hasExpandedStrip = expandedCat != null && expandedChildren.isNotEmpty()
val donutCats    = if (hasExpandedStrip) expandedChildren else categories
val donutExpense = if (hasExpandedStrip && selectedTab == 0)
    expandedChildren.sumOf { spending[it.id] ?: 0.0 } else totalExpense
val donutIncome  = if (hasExpandedStrip && selectedTab == 1)
    expandedChildren.sumOf { spending[it.id] ?: 0.0 } else totalIncome
DonutChart(
    categories   = donutCats,
    spending     = spending,
    totalExpense = donutExpense,
    totalIncome  = donutIncome,
    ...
)
```

`expandedChildren` is already computed for the expansion strip (same-name-dedup applied). The donut uses the same `spending` map as the chips — no separate data fetch needed.

**Visual contract:** When subcategory strip is visible, the donut ring shows slices for each child category proportional to their individual spending. The center label totals reflect only child spending for the active tab. When the strip is dismissed, the donut reverts to showing all root categories.

**Rule:** Do not pass the full `categories` list to DonutChart when `hasExpandedStrip == true`. Passing all categories while showing a strip makes the donut misleading — the expanded parent's slice stays dominant and the children are invisible in the ring.

## ADR-035: Account Icons Show Currency Symbol Badge (2026-05-31)

Every account icon has a small circular badge at the bottom-right corner showing the currency symbol (₴, $, €, £, ₿…). Symbol is resolved from `CURRENCIES_ALL` (130+ currencies in `ui/settings/data/CurrencyData.kt`), truncated to 2 chars. Falls back to the first 2 chars of the currency code.

**Affected composables:**
- `AccountIconBox` in `AccountsScreen.kt` — 20dp circle, 8sp Bold text
- `AccountPickerSheet` in `CalcDateSheet.kt` — 16dp circle, 7sp Bold text

**Rule:** The badge uses `MaterialTheme.colorScheme.surface` as background. Text color is adaptive — see ADR-037. The star badge (default account indicator, `Alignment.BottomStart`) and the currency badge (`Alignment.BottomEnd`) never overlap.

## ADR-037: Adaptive Icon Tint For Light-Colored Account Backgrounds (2026-05-31)

Account icons and card content that sit on `colorHex` background must use `luminance()` to choose a readable tint. White-on-white is unreadable when an account color is near `#FFFFFF`.

**Pattern:**
```kotlin
val isLightBg  = accentColor.luminance() > 0.5f
val iconTint   = if (isLightBg) Color(0xFF1C1B1F) else Color.White
val badgeColor = if (isLightBg) Color(0xFF1C1B1F) else accentColor
```

**Why `0xFF1C1B1F`:** Material 3 "on-surface" dark token — near-black, readable on any light background including white.

**Affected composables (as of 2026-06-01):**
- `AccountIconBox` (`AccountsScreen.kt`) — icon tint + currency badge text
- `AccountActionSheet` card (`AccountPickerSheets.kt`) — all card content (icon box bg, icon tint, name text, balance text, star icon) uses `onCard`
- `AccountFormSheet` icon preview (`AccountSheets.kt`) — `iconTint`
- `AccountPickerSheet` (`CalcDateSheet.kt`) — header `contentColor` + per-row `itemContentColor`; `FallbackAccountColor=#3949AB`, `DarkOnLightColor=#1C1B1F`

**Rule:** Never hardcode `tint = Color.White` on an icon or text where the background comes from `account.colorHex`. Always compute from `luminance()`. The threshold `> 0.5f` is a standard WCAG-adjacent approximation — do not lower it without measuring contrast ratios.

## ADR-036: Subcategory Mode Shows Only Subcategories (2026-05-31)

`showSubcategories = true` must display **only items with `parentId != null`**. Before this fix, the main `CategoriesScreen` passed `allCategoriesForTab` (all categories, including roots) to `CategoriesGridContent`, and the `sorted` algorithm grouped roots with their children — causing root categories to appear interleaved with subcategories.

**Fix — two changes:**

1. **`CategoriesScreen.kt`** (main screen): `categories` is now filtered to subcategories only when in subcategory mode:
   ```kotlin
   val categories = if (!state.showSubcategories) {
       allCategoriesForTab.filter { it.parentId == null }
   } else {
       allCategoriesForTab.filter { it.parentId != null }
   }
   ```
   `EditCategoriesScreen` already applied this filter correctly.

2. **`CategoriesGridContent`**: Removed the complex `sorted` grouping (`roots.flatMap { [root] + children } + orphans`). Replaced with a simple sort:
   ```kotlin
   val sorted = categories.sortedByDescending { spending[it.id] ?: 0.0 }
   ```
   `parentColors` now resolves parent colors from `allCategoriesForTab` (which still contains all categories) instead of from `categories` (which no longer contains roots in subcategory mode).

**Rule:** Do not restore the `roots.flatMap { }` grouping logic. The caller is responsible for passing the correct set of categories — `CategoriesGridContent` just sorts and renders what it receives.

## ADR-034: AccountsScreen Action Callbacks Are Wired In MainScreen (2026-05-31)

`AccountsScreen` exposes four action callbacks — `onViewTx`, `onAddIncome`, `onAddExpense`, `onAddTransfer` — that default to `{}` if not passed. Prior to this fix they were omitted from the `AccountsScreen` call in `MainScreen`, making four of the six `AccountActionSheet` buttons silently do nothing.

**Current wiring** (in `MainScreen.kt`):
```kotlin
onViewTx      = { scope.launch { pagerState.animateScrollToPage(txTabIndex) } }
onAddIncome   = { onAddTransaction() }
onAddExpense  = { onAddTransaction() }
onAddTransfer = { onAddTransaction() }
```

**Known limitation:** The `AccountEntity` parameter is available in each callback but is not forwarded to the `AddTransaction` screen. All three transaction-creation actions open the form without a pre-selected account. To fix this, the NavGraph route or `AddTransactionScreen` would need to accept an `accountId` argument.

**Rule:** When adding new action callbacks to `AccountsScreen`, always wire them in `MainScreen` — never leave them at the default `{}`. A silent no-op is indistinguishable from a crash to the user.

## ADR-034: Overview Screen Uses `categoryIconFor` From `CategoryIcons.kt` (2026-05-31)

`OverviewScreen.kt` previously had its own local `iconVectorFor()` function with only 13 icon mappings. Any icon name not in that list fell back to `Icons.Default.Category` — causing wrong icons for "Переказ" (transfer), "Доставка" (delivery), "AliExpress" (aliexpress), "Електроніка" (devices), and others.

**Fix:** Removed `iconVectorFor()` from `OverviewScreen.kt`. Now calls `categoryIconFor(iconName)` from `org.syalosovetskyi.onemoney.ui.categories.CategoryIcons`, which covers all 48 icon keys. `categoryIconFor` was changed from `internal` to `public` to allow cross-package access.

**Rule:** Do not add a new local icon mapper in any screen file. Always call `categoryIconFor()` from `CategoryIcons.kt`. If a new icon key is added to the app, add it to `CATEGORY_ICONS_LIST` in `CategoryIcons.kt` — that is the single source of truth for all icon name → vector mappings.

## ADR-038: AccountEntity Has Credit Limit Field Stored In DB (2026-05-31)

`AccountEntity.creditLimit: Double` (default `0.0`) stores the credit limit for card/debt accounts. Added via migration 26→27 (`ALTER TABLE accounts ADD COLUMN creditLimit REAL NOT NULL DEFAULT 0.0`).

**UI wiring:**
- "Кредитний ліміт" row in `AccountFormSheet` ("Баланс" section) opens `AmountCalculatorSheet` on tap
- State is `var creditLimit by remember { mutableStateOf(existing?.creditLimit ?: 0.0) }`
- Included as the **9th parameter** of `onSave` in `AccountFormSheet`
- All callers (`AccountsScreen.kt`, `MainScreen.kt`) destructure and forward it

**Rule:** The `AccountFormSheet.onSave` callback has 9 parameters as of migration 26→27. Do not add new fields to `AccountEntity` without: (1) adding a migration, (2) updating `onSave` and all callers, (3) updating this doc and `DB_SCHEMA.md`.

## ADR-039: AddTransactionScreen Currency Key Opens CurrencyPickerSheet (2026-05-31)

The currency symbol key in `AddTransactionScreen`'s `SharedCalcKeypad` opens `CurrencyPickerSheet` so the user can override the transaction's display currency.

**State pattern:**
```kotlin
var selectedCurrency by remember { mutableStateOf(fromAccount?.currency ?: "UAH") }
LaunchedEffect(fromAccount?.currency) { selectedCurrency = fromAccount?.currency ?: "UAH" }
val currencySymbol = remember(selectedCurrency) {
    CURRENCIES_ALL.find { it.code == selectedCurrency }?.symbol ?: selectedCurrency
}
```

**`CurrencyPickerSheet` change:** Added `title: String = "Валюта рахунку"` parameter so the sheet can be reused across contexts. Default is unchanged; `AddTransactionScreen` passes "Валюта транзакції".

**Rule:** `selectedCurrency` is UI-only display state — `TransactionEntity` has no `currency` field. Do not persist it to the DB without adding a migration and a new entity field.

**Rule:** Account-switch resets `selectedCurrency` to the new account's currency via `LaunchedEffect(fromAccount?.currency)`. This is intentional — the account is the natural source of currency for a transaction.

## ADR-035: CategoryFormSheet Has Per-Category Currency Picker (2026-05-31)

`CategoryFormSheet` includes a "Валюта категорії" `ListItem` row in the "Налаштування" section. Tapping it opens `CurrencyPageContent` as a full-screen `Dialog` overlay — the same pattern used by `ColorIconPickerSheet`.

- `currencyCode: String` state defaults to `"UAH"` (or `existing?.currencyCode` when editing). Stored in `CategoryEntity.currencyCode`.
- The row's `supportingContent` shows the live label looked up from `CURRENCIES_MAIN + CURRENCIES_OTHER + CURRENCIES_CRYPTO` (e.g. `"Українська гривня – ₴"`).
- `CurrencyPageContent` (in `SettingsSubScreens.kt`) has an optional `title: String = "Валюта за замовчуванням"` param. Called with `title = "Валюта категорії"`.
- `onSave` lambda signature has `currencyCode: String` as the **8th parameter** (after `archived`).
- `CategoriesViewModel.add()` has `currencyCode: String = "UAH"` and `parentId: Long? = null` params.
- `MainScreen` `onSave` lambda destructures `currency` and passes it to both `existing.copy(currencyCode = currency)` and `categoriesViewModel.add(..., currencyCode = currency)`.

DB: `CategoryEntity.currencyCode TEXT NOT NULL DEFAULT 'UAH'` added via migration 20→21.

## ADR-035: Extended Icon Set And `repairIconKeys()` Startup Migration (2026-05-31)

14 new icon keys were added to `CATEGORY_ICONS_LIST` in `CategoryIcons.kt` to cover categories that previously showed the generic `Category` fallback icon (triangle+square):

| Key | Material Icon | For |
|---|---|---|
| `server` | `Storage` | Хостінг, VPS |
| `flower` | `LocalFlorist` | Квіти |
| `souvenir` | `Redeem` | Сувеніри (distinct from gift/`CardGiftcard`) |
| `store` | `Storefront` | Rozetka, eBay, Маркетплейс |
| `shoes` | `AutoMirrored.DirectionsWalk` | Взуття |
| `tools` | `Build` | Інструменти |
| `hardware` | `Foundation` | Будматеріали |
| `toys` | `SmartToy` | Іграшки |
| `fitness` | `FitnessCenter` | Спортивні товари |
| `dental` | `Healing` | Стоматолог |
| `train` | `Train` | Залізниця |
| `hotel` | `Hotel` | Готель |
| `book` | `MenuBook` | Книги |
| `auto_parts` | `Handyman` | Автозапчастини |

**Why:** Many user-created and MonoFlow-imported categories had icon keys not in `CATEGORY_ICONS_LIST`, so the UI showed a generic icon. Some seeder categories also had wrong keys (e.g., "взуття" → "clothes" hanger, "краса" → "shopping" cart).

**`repairIconKeys()` in `CategoryRepository`:** Called in `onemoneyApp.seedInitialData()` on every app start (idempotent). It applies fixes in priority order:
1. **Name overrides** — explicit `nameOverrides` list (e.g., "взуття" → "shoes", "ebay" → "store", "комунал" → "home", "язок" → "phone", "інтернет" → "wifi") applied regardless of current stored key.
2. **Generic icon fix** — for any category whose icon is `"category"` or `"family"` (valid but generic), runs `suggestCategoryStyle(name, type)`; applies the result if it's more specific than `"category"`. This catches categories imported from MonoFlow or backups that had a generic icon after prior migrations ran.
3. **Invalid key fix** — for any remaining category whose icon key is not in `validKeys`, re-runs `suggestCategoryStyle(name, type)`.

**Rule:** When adding a new icon key to `CATEGORY_ICONS_LIST`, also add it to the `validKeys` set inside `repairIconKeys()` and to `iconColorMap` in `CategoryStyleUtil.kt`. All three must stay in sync. Do not add icon keys to `CATEGORY_ICONS_LIST` without a corresponding `suggestCategoryStyle` rule — otherwise auto-suggest will never assign the new key to new categories.

## ADR-040: BudgetInputSheet Has In-Sheet Currency Picker (2026-05-31)

`BudgetInputSheet` (used for both expense and income category budgets) allows the user to change the budget currency via the currency key in `SharedCalcKeypad`.

**State:**
- `pickedCurrency` initialised from `catRow.category.currencyCode`, resynced via `LaunchedEffect(catRow.category.id)`.
- `currencySymbol` derived from `CURRENCIES_ALL` lookup.

**Currency picker:** `onCurrencyClick = { showCurrencyPicker = true }` is passed to `SharedCalcKeypad`. Tapping the currency key opens a full-screen `Dialog` (`usePlatformDefaultWidth = false`) containing `CurrencyPageContent` (3 tabs: Основні / Інші / Крипто — 130+ currencies). The Dialog is placed **after** the main `ModalBottomSheet` in the composition tree so it renders on top. A previous implementation used a nested `ModalBottomSheet` — it silently appeared behind the main sheet and was invisible to the user.

**`SharedCalcKeypad` parameter:** `onCurrencyClick: (() -> Unit)? = null`. When non-null, the currency key uses `primaryContainer` background (interactive cue); when null, `surfaceVariant` (inert).

**`onConfirm` callback:** `(Double, String) -> Unit` — both the new budget amount and the selected currency code are passed to the caller.

**Rule:** Do not use `remember(someState?.property)` for deriving a symbol from a nested state object — use a separate `var` state with a `LaunchedEffect` sync instead. The `remember` pattern misses the initial render when the parent state is already set.

**Rule:** `BudgetInputSheet` owns the currency picker for category budgets. The initial currency comes from the category entity — the user may override it per-session but the change only persists when they confirm.

**Rule:** Do not use a nested `ModalBottomSheet` for a picker that must appear on top of another `ModalBottomSheet`. Compose renders composables in declaration order — a sheet declared before the parent sheet appears behind it. Always use `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` for overlays on top of sheets.

## ADR-040: Amount Display Currency Symbol Is A Separate Clickable Text (2026-05-31)

The "0 ₴" amount row in `AddTransactionScreen` is a `Row` containing **two separate `Text` composables**, not one combined string:

```kotlin
Row(verticalAlignment = CenterVertically) {
    Text(calc.displayExprNoSymbol(), fontSize = 34.sp, ...)     // numeric — not clickable
    Text(" $currencySymbol", fontSize = 34.sp,                  // symbol — clickable
        modifier = Modifier.clickable { showCurrencyPicker = true })
}
```

This gives the user a second tap target for `CurrencyPickerSheet`, in addition to the keyboard currency key already documented in ADR-039.

**`CalcStateHolder.displayExprNoSymbol()`** (new method): returns the numeric expression without the symbol. `displayExpr(symbol)` now delegates to it: `"${displayExprNoSymbol()} $symbol"`. All existing callers of `displayExpr` are unaffected — output is identical.

**Rule:** Do not collapse the numeric and symbol parts back into a single `Text`. A monolithic string cannot carry a partial tap target. If the amount row needs interactive behaviour, keep the symbol as a separate composable.

## ADR-041: CategoryPickerSheet Simplified Mode Via `currentType` (2026-05-31)

`CategoryPickerSheet` previously always showed 3 tabs (Income / Expense / Transfer) regardless of context. `AddTransactionScreen` needed a version that opens directly to the current transaction type without letting the user change type via tabs.

**Decision:** Add `currentType: TransactionType? = null` parameter. When non-null, the sheet renders in **simplified mode**: no `TabRow`, single type header row, smaller height (55% vs 67% screen). Category grid or account list shown based on `currentType`. When null — full 3-tab layout (default, used by search and other callers).

**`AddTransactionScreen` right-panel migration:**
- Before: `DropdownMenu` for both category and transfer to-account.
- After: `CategoryPickerSheet(currentType = state.type)` — one sheet handles all three types.
- Right panel label: "До категорії" for EXPENSE/INCOME, "На рахунок" for TRANSFER.
- `onSelect` callback also calls `viewModel.setType(cat.type)` to keep type in sync with the chosen category.

**`initialTab: Int = 1`** (default = Expense tab, index 1): used by full-tab callers to open the correct tab based on context.

**Rule:** Do not revert to `DropdownMenu` for category or transfer-account selection in `AddTransactionScreen`. The `CategoryPickerSheet` path provides consistent UX (search, spending amounts, account list) that a `DropdownMenu` cannot.

**Rule:** Do not add a 4th tab or nested navigation inside `CategoryPickerSheet`. Complex category browsing belongs in `EditCategoriesScreen` (full-screen). The picker is a quick selection surface only.

## ADR-042: Budget Screen Row Layout — Spending Below Icon, Overbudget Pill Badge (2026-05-31)

`BudgetCatFullRow` was redesigned to match the 1Money reference layout.

**Previous layout:**
- Left: icon circle | name + spent amount (left column)
- Right: remaining (negative red when overbudget)

**New layout:**
```
[52dp circle]   CategoryName          139 ₴   ← remaining in accentColor
  spent ₴ (bold, category color below circle)  в бюджеті 200 ₴  ← grey small
```

- Spending amount moved below the icon circle (small Bold, category color).
- **Within budget:** remaining shown as plain positive text in `accentColor`.
- **Overbudget:** `Surface(RoundedCornerShape(50), color=accentColor)` pill badge in white text showing the absolute overspend (`spent − budget`).

**Progress bar background (added 2026-05-31, refined 2026-05-31):** `BudgetCatFullRow` uses `Modifier.drawBehind` instead of `.background()`:
- Base layer: `drawRect(Color.White)` — full row, so the unfilled area is clean white.
- Fill layer: `drawRect(color.copy(alpha=0.20f), size=Size(width * progress, height))`.
- `progress = (spent / budget).coerceIn(0.0, 1.0)` — overbudget clamps to 1.0 (full fill).
- Do **not** replace `drawBehind` with `.background()`. Do **not** use a tinted base — unfilled area must be white.
- Chip row (unbudgeted categories) also uses `Color.White` background for visual consistency.
- **Section header row** (title + totals) uses the same `drawBehind` pattern: `headerProgress = (totalAmount / totalBudget).coerceIn(0, 1)`. White base + `accentColor.copy(alpha=0.20f)` fill. When `totalBudget == 0` — no fill (white). Do not give the header a flat `background()` while rows use `drawBehind`.

**`MoreLessChip` change:** `remaining: Int` parameter replaced with `hiddenTotal: Double`. The chip now shows `"${formatMoney(hiddenTotal)} ₴"` below the chevron (sum of all hidden categories' spending) instead of `"+N"` count. `BudgetSectionCard` computes `hiddenChipTotal = chipRows.drop(3).sumOf { it.amount }`.

**`BudgetSectionCard` — `incomeMode` removed (2026-05-31):** Both income and expense sections now use identical row/chip logic — categories with `budgetAmount > 0` render as full rows (`BudgetCatFullRow`); categories with `budgetAmount == 0` and `amount > 0` render as chips. Income progress bar shows `received / budget` exactly like expenses.

**Rule:** Income categories with `budgetAmount > 0` **must** render as full rows. Do not reintroduce an `incomeMode` flag that forces income to chips — income budget rows give the user the same at-a-glance received/remaining breakdown as expense budget rows.

**Rule:** Do not revert overbudget display to a negative number in error color. The pill badge makes the overbudget state prominent without being alarming — the row background already signals the state.

## ADR-042b: Repeat Transaction Feature — Full Stack (2026-05-31)

Повторение и напоминания для транзакций реализованы end-to-end. До этого ADR они были UI-only stubs (данные терялись при подтверждении).

### DB (migration 27→28)

```
transactions.repeatMode:     TEXT NOT NULL DEFAULT 'NEVER'
transactions.reminderMode:   TEXT NOT NULL DEFAULT 'NEVER'
transactions.nextRepeatDate: INTEGER (nullable Long)
```

`nextRepeatDate` — UTC timestamp следующего автоматически создаваемого вхождения. `null` = обычная транзакция или серия исчерпана.

### Жизненный цикл

1. Пользователь выбирает `repeatMode` / `reminderMode` через `CalcDateSheet → RepeatDialog / ReminderDialog` в `QuickExpenseSheet`.
2. `onSave(accountId, amount, note, date, repeatMode, reminderMode)` (6 параметров) передаёт данные в `CategoriesViewModel.recordTransaction` / `TransactionsListViewModel.recordTransaction`.
3. ViewModel вычисляет `nextRepeatDate = calculateNextRepeatDate(date, repeatMode)` и сохраняет все 3 поля в `TransactionEntity`.
4. `RepeatTransactionWorker` (ежесуточно в 00:01 + при каждом старте приложения):
   - Находит `nextRepeatDate <= today` → создаёт копию транзакции с `date = nextRepeatDate`, `nextRepeatDate = следующее`, обновляет баланс счёта (`AccountDao.updateBalance`), сбрасывает `nextRepeatDate = NULL` у «использованного» шаблона.
   - Повторяет пока не закончатся просроченные вхождения (catch-up за несколько дней без запуска).
   - Для транзакций с `reminderMode != 'NEVER'`: если `startOfDay(nextRepeatDate - offset_days) == today` → отправляет уведомление в канале `onemoney_repeat_reminder`.

### Ключевые артефакты

| Файл | Роль |
|---|---|
| `util/RepeatUtil.kt` | `calculateNextRepeatDate`, `startOfDay`, `reminderOffsetDays` |
| `workers/RepeatTransactionWorker.kt` | Воркер; Hilt EntryPoint (`TransactionDao`, `AccountDao`) |
| `TransactionDao` | `getDueRepeatTransactions(today)`, `clearNextRepeatDate(id)`, `getTransactionsWithReminder()` |

### UX-исправление: нулевая сумма

`QuickExpenseSheet.onConfirm()` теперь при `amt == 0.0` устанавливает `amountError = true`, что запускает `animateColorAsState(error)` с флэшем на 600мс, вместо молчаливого игнорирования нажатия.

### Правила

**Rule:** `nextRepeatDate` — всегда дата БУДУЩЕГО вхождения. После создания вхождения оно обнуляется у шаблона. Никогда не читайте его как «дата последнего создания».

**Rule:** Баланс счёта в воркере обновляется inline (дублирует логику `TransactionRepository.addTransaction`). При изменении логики баланса в репозитории — синхронизировать с воркером.

**Rule:** `QuickExpenseSheet.onSave` имеет **7 параметров** (`categoryId: Long` добавлен последним). `TransferQuickSheet` и `AddTransactionScreen` используют свои независимые save-пути и не включают repeat (Transfer и AddTx form — отдельный UX).

**Rule:** `BackupSerializer` сериализует новые поля. При восстановлении из старого бэкапа (без полей) используются `optString/optLong` с дефолтами `"NEVER"` / `null` — обратная совместимость гарантирована.

## ADR-043: EditCategoriesScreen Subcategories Filter Fix (2026-05-31)

**Problem:** In `EditCategoriesScreen`, switching to "Субкатегорії" mode showed ALL categories (roots + children) instead of only child categories.

**Root cause:** `localCats.toList()` returned the full list without filtering. The linter periodically rewrites `EditCategoriesScreen.kt` and resets the fix.

**Fix (line ~62):**
```kotlin
val categories = if (!showSubcategories)
    localCats.filter { it.parentId == null }
else
    localCats.filter { it.parentId != null }  // was: localCats.toList()
```

**Rule:** If the linter rewrites `EditCategoriesScreen.kt`, the `else` branch for `showSubcategories` must filter `parentId != null`. `localCats.toList()` (unfiltered) is always wrong in this branch.

## ADR-044: BudgetInputSheet — Icon Click + Currency Picker (2026-05-31)

`BudgetInputSheet` (category budget entry, `BudgetSheets.kt`) gained two new capabilities:

**1. Category icon click (`onIconClick: (() -> Unit)? = null`)**
- The floating icon circle (top-right) becomes clickable when `onIconClick` is non-null.
- In `BudgetScreen`, income `BudgetInputSheet` wires `onIconClick = { incomeCatToEdit = null; showIncomeBudgetSheet = true }` — tapping the icon returns to the income category picker.
- Expense `BudgetInputSheet` (inside `BudgetSectionCard`) does not pass `onIconClick` (defaults to null = inert).

**2. Currency picker**
- `var pickedCurrency by remember { mutableStateOf(catRow.category.currencyCode) }`
- `LaunchedEffect(catRow.category.id)` syncs when the row changes.
- `currencySymbol` derived directly (not via `remember(key)` — avoids anti-pattern).
- `onCurrencyClick = { showCurrencyPicker = true }` passed to `SharedCalcKeypad`.
- Currency picker: `Dialog(usePlatformDefaultWidth=false)` with `CurrencyPageContent` (130+ currencies, 3 tabs). Placed **after** the main `ModalBottomSheet` closing brace so it renders on top. Do not use a nested `ModalBottomSheet` — it renders behind the parent sheet (see ADR-040 fix note).
- `onConfirm` signature: `(Double, String) -> Unit` — passes `(amount, currency)`.
- `BudgetViewModel.updateCategoryBudget` now accepts optional `currency: String = category.currencyCode` and saves `currencyCode` alongside `budgetAmount`.

**Rule:** `onConfirm` in `BudgetInputSheet` always passes both amount AND currency. Any new caller must handle both params.

## ADR-045: QuickExpenseSheet — Category Panel + Currency + In-Sheet Category Picker (2026-05-31, updated 2026-06-01)

`QuickExpenseSheet` (`ui/categories/CategorySheets.kt`) is the quick-entry transaction sheet opened by tapping a category chip in the categories grid or `TransactionsListScreen`. Not `AddTransactionScreen`, which is a separate full-screen navigation destination.

**Category panel (current state as of 2026-06-01):**
- `CatPanel` has `.clickable(enabled = categories.isNotEmpty()) { showCatPicker = true }`.
- Tapping it opens an **inline ModalBottomSheet** category picker — does NOT dismiss the sheet.
- Picker shows root categories grouped into "Витрати" / "Доходи" with icon + checkmark on currently selected.
- On selection: `selectedCategory = cat` — header color/icon/name update immediately.
- `var selectedCategory by remember(category) { mutableStateOf(category) }` tracks the in-sheet choice.
- `onSave` receives `selectedCategory.id` as `categoryId: Long` (7th parameter).

**`onSave` signature (7 parameters):**
```kotlin
onSave: (accountId: Long, amount: Double, note: String, date: Long,
         repeatMode: String, reminderMode: String, categoryId: Long) -> Unit
```
Callers resolve: `categories.firstOrNull { it.id == categoryId } ?: initialCat`.

**`categories: List<CategoryEntity>` parameter** (default `emptyList()`):
- Passed by both `CategoriesScreen` (`state.expenseCategories + state.incomeCategories`) and `TransactionsListScreen` (same).
- When empty: category panel is NOT clickable (no picker available).

**Currency support:**
- `var selectedCurrency` initialised from `selectedAccount?.currency ?: "UAH"`.
- `LaunchedEffect(selectedAccount?.currency)` syncs when account changes.
- `currencySymbol` derived from `CURRENCIES_ALL`.
- `SharedCalcKeypad(onCurrencyClick = { showCurrencyPicker = true }, ...)` — ₴ key highlighted.
- `CurrencyBottomSheet` (ModalBottomSheet) shown when `showCurrencyPicker = true`. NOT Dialog — see ADR-051.

**Rule:** Always check `CategorySheets.kt / QuickExpenseSheet` (not `AddTransactionScreen`) when debugging the quick-entry transaction sheet opened from the categories grid or `TransactionsListScreen`.

**Rule:** `onSave` has 7 parameters. Do not revert to 6.

## ADR-046: Light Theme surfaceVariant Lightened (2026-05-31)

**Problem:** `surfaceVariant` was `#E2E1EC` — a visible grayish-purple that made keypad buttons, cards, and chip backgrounds look grey rather than white.

**Change (`Theme.kt` light palette):**
| Token | Before | After |
|---|---|---|
| `surfaceVariant` | `#E2E1EC` | `#F0EFF6` |
| `outlineVariant` | `#C6C5D0` | `#D8D7E3` |

`background` and `surface` stay `#FFFFFF` — they were already pure white.

**Rule:** `surfaceVariant` must remain perceptibly lighter than `#E8E8F0` in light mode. Do not revert to `#E2E1EC` — the goal is a whiter overall feel. Dark mode palette unchanged.

## ADR-047: EditCategoriesScreen BackHandler (2026-05-31)

**Problem:** Pressing the system Back button while `EditCategoriesScreen` was open exited the app instead of closing the overlay.

**Root cause:** `MainScreen` had only `BackHandler(enabled = currentPage != homeTabIndex)`. When the user was on the home tab (e.g. "Категорії") and opened Edit Categories, the tab handler was disabled, so Back fell through to the system.

**Fix (`MainScreen.kt`, before the tab handler):**
```kotlin
BackHandler(enabled = showEditCategories) { showEditCategories = false }
BackHandler(enabled = currentPage != homeTabIndex) { goBack() }
```

Compose processes `BackHandler`s last-registered-first, so the overlay handler always takes priority over tab navigation.

**Rule:** Every full-screen overlay (`showEditCategories`, `showSettingsScreen`, `showDataScreen`, etc.) must have its own `BackHandler` registered before the tab-navigation handler. Priority order (top = highest): EditCategories → SettingsScreen → DataScreen → tab navigation → system.

## ADR-048: Drag-and-Drop In CategoriesGridContent — Gesture Conflict Fix (2026-05-31)

`CategoriesGridContent` supports chip drag-and-drop reorder via `onChipDragSwap: ((Long, Long) -> Unit)?`. When this callback is non-null, every chip's `extraModifier` gains `detectDragGesturesAfterLongPress` (parent Box level) + position tracking + alpha.

**Root cause of the original bug:** Compose pointer events in Main pass go child→parent. `CategoryChip` uses `combinedClickable` which adds a long-press detector when `onLongClick` is non-null (even `{}`). On long press, `combinedClickable` fires `onLongClick` and **consumes** the event — the parent's `detectDragGesturesAfterLongPress` fires `onDragStart` (chips briefly flash) but then gets `onDragCancel` immediately because all subsequent drag events are consumed.

**Fix — `suppressLongPress` parameter:**
```kotlin
// CategoryGridSlot
suppressLongPress: Boolean = false
// ...
onLongPress = if (suppressLongPress) null else ({ onChipLongClick(category) })
```
When `onLongPress = null`, `combinedClickable` does NOT add a long-press detector, so it never consumes events during a long press. The parent's `detectDragGesturesAfterLongPress` can then claim drag events freely.

`CategoriesGridContent` passes `suppressLongPress = (onChipDragSwap != null)` to every `CategoryGridRow` and direct `CategoryGridSlot` call.

**Ghost chip overlay:**
During drag, the original chip slot becomes alpha 0. A floating `Box` rendered as a sibling of `LazyColumn` (inside an outer `Box`) displays a full `CategoryChip` at the finger's current position:
```
ghostX = chipCenters[draggingId].x − containerRootPos.x + dragOffset.x − chipPxW/2
ghostY = chipCenters[draggingId].y − containerRootPos.y + dragOffset.y − chipPxH/2
```
`dragOffset` is a `mutableStateOf(Offset.Zero)` updated on every `onDrag` delta. `containerRootPos` is tracked via `onGloballyPositioned` on the outer `Box`.

**UX:** Long press → chip becomes invisible + ghost appears at finger → drag → nearest chip highlighted (alpha 1.0, others 0.7) → release → chips swap positions.

**Rule:** In `EditCategoriesScreen`, always pass `onChipLongClick = {}` (not a form-opening lambda) so the edit form does not interfere with drag. Tap opens the form; long press starts drag.

**Rule:** Do not use `onLongClick = {}` (empty lambda) when a gesture detector in a parent box needs the long press. Use `onLongClick = null` to fully disable `combinedClickable`'s long-press handling.

## ADR-049: QuickExpenseSheet Visual Redesign (2026-06-01)

**Problem:** The sheet had a white drag-handle strip at top, small (34dp) category icons, blue-background account panel, right-aligned category text, green-only confirm button.

**Changes (`CategorySheets.kt` — `QuickExpenseSheet`):**

1. **Drag handle removed:** `dragHandle = {}` — no white strip, sheet content starts at the rounded corners.

2. **Header restructured** (total height: 32dp top gap + 80dp row = 112dp `Box`):
   - **Left panel** (account): `MaterialTheme.colorScheme.surface` bg (white). Small 28dp card icon (surfaceVariant rounded box + CreditCard icon) at TopStart. Label + account name at BottomStart.
   - **Right panel** (category): `catColor` bg. Label + category name at BottomStart, left-aligned. End padding `68.dp` reserves space for floating icon.
   - **Floating icon**: 64dp `CircleShape`, `catColor` bg, positioned `align(Alignment.TopEnd).offset(x = -12.dp)` at `y = 0` of the outer `Box`. Since the `Box` has `padding(top = 32.dp)` on the Row, the icon's bottom half overlaps the Row — creating the visual effect of the icon "protruding above" the panel row.

3. **Notes field:** `textStyle = bodySmall`, placeholder uses `FontStyle.Italic`, `TextAlign.Center`, `alpha = 0.4f`.

4. **Confirm button color = `catColor`:** `SharedCalcKeypad(confirmColor = catColor, ...)`. Was always green (`Color(0xFF4CAF50)`).

5. **`accountColor = Color(0xFF3949AB)` removed** — no longer used.

**Rule:** The account panel background is `MaterialTheme.colorScheme.surface` (not a hardcoded color). Do not restore a colored account panel without a product decision.

**Rule:** The floating 64dp icon is at `y = 0` inside the header `Box`, overlapping with the `padding(top = 32.dp)` Row. Do not add a negative `offset(y = ...)` — that clips at the ModalBottomSheet boundary.

**Rule:** `confirmColor` in `SharedCalcKeypad` must equal `catColor` in `QuickExpenseSheet` so the confirm button matches the active category.

## ADR-050: CategoryActionSheet Icon Spacer Reduced (2026-06-01)

**Problem:** There was 40dp of whitespace between the floating icon and the category name in `CategoryActionSheet`, making the header feel empty.

**Fix (`CategorySheets.kt`, inside the colored header `Column`):**
```kotlin
Spacer(Modifier.height(16.dp))  // was: 40.dp
```

The floating icon (72dp, `offset(y = -36.dp)`) extends 36dp into the card and card padding is 20dp — so minimum needed clearance is 36 - 20 = 16dp. 40dp was over-reserved.

**Rule:** The spacer in `CategoryActionSheet`'s colored header is 16dp. Do not increase it unless the icon size or offset changes.

## ADR-051: CurrencyBottomSheet — ModalBottomSheet Variant For Screen-Level Currency Pickers (2026-06-01)

**Problem:** `CurrencyPickerSheet` (Dialog with `usePlatformDefaultWidth = false`) fails to appear when used from a regular navigation screen (`AddTransactionScreen`, `QuickExpenseSheet`). Tap → `showCurrencyPicker = true` → Dialog is rendered but immediately dismissed before the user sees it. Root cause: Android dispatches `ACTION_UP` to the new Dialog window before `Surface(fillMaxSize)` renders, touching the transparent margin → `dismissOnClickOutside = true` fires. Setting `dismissOnClickOutside = false` did not fully solve the issue.

**Solution:** Added `CurrencyBottomSheet` to `ui/components/currency/CurrencyPicker.kt` — same 3-tab currency list but wrapped in `ModalBottomSheet(skipPartiallyExpanded = true, fillMaxHeight(0.92f))`.

**Usage:**
| Caller | Component | Reason |
|---|---|---|
| `AddTransactionScreen` | `CurrencyBottomSheet` | Screen-level composable — Dialog fails |
| `QuickExpenseSheet` | `CurrencyBottomSheet` | Screen-level composable — Dialog fails |
| `BudgetInputSheet` | `CurrencyPickerSheet` (Dialog) | Already inside a ModalBottomSheet — Dialog works; nested ModalBottomSheet renders behind parent |
| `AccountFormSheet` | `CurrencyPickerSheet` (Dialog) | Already inside a ModalBottomSheet — Dialog works |

**Rule:** Use `CurrencyBottomSheet` when the currency picker is opened from a **navigation screen** (not from inside a ModalBottomSheet). Use `CurrencyPickerSheet` Dialog only from **inside an existing ModalBottomSheet** — a nested sheet renders behind its parent (see ADR-040 fix note in `BudgetInputSheet`).

**Rule:** Do not replace `CurrencyPickerSheet` with `CurrencyBottomSheet` in `BudgetInputSheet` or `AccountFormSheet` — a ModalBottomSheet nested inside another ModalBottomSheet is invisible to the user.

## ADR-052: Floating Icon Pattern — Dialog vs ModalBottomSheet (2026-06-01)

**Context:** Category detail sheets use a floating icon at the top-right with different overflow behavior depending on the container.

### CategoryActionSheet (custom Dialog — CategorySheets.kt)

The panel is a plain `Box(align=BottomStart)` with no `clipToBounds()`, so the icon overflows ABOVE the card.

- Icon: sibling child of content `Column` inside the panel `Box`
- `offset(y = -36.dp)` (half of 72dp) → top half in scrim area, visible over the dim overlay
- Header column starts with `Spacer(16.dp)` to clear the icon's bottom half inside the card

**Rule:** Icon must NOT be inside `Column(clip=RoundedCornerShape(...))` — the clip cuts it. It must be a sibling overlay inside the outer unclipped `Box`.

### CategoryDetailSheet (ModalBottomSheet — OverviewSheets.kt)

Sheet shape clips anything above its top rounded corners. Icon stays within sheet bounds.

- Icon: `align=TopEnd`, `offset(y = -28.dp)`, 72dp — extends into drag-handle area (same `containerColor`)
- Style: `MaterialTheme.colorScheme.surface` bg + 2dp `catColor` border + `catColor` tint
- Content column starts with `Spacer(46.dp)` to clear the icon

**Rule:** For ModalBottomSheet, negative y offset must not exceed drag-handle height or the icon will be clipped.

## ADR-053: SharedMonthNavPill Current-Month Color (2026-06-01)

**Problem:** Single crimson accent gave no visual signal whether the user is viewing the current period.

**Decision:** Two accent constants in `SharedMonthPill.kt`:

```kotlin
private val PILL_ACCENT  = Color(0xFFD81B60)  // crimson — past/future months
private val PILL_CURRENT = Color(0xFF4B6BEF)  // indigo-blue — current calendar month
```

Detection:
```kotlin
val isCurrentMonth = appMonth.mode == PeriodMode.MONTH &&
                     appMonth.month == today.get(Calendar.MONTH) &&
                     appMonth.year  == today.get(Calendar.YEAR)
val pillColor = if (isCurrentMonth) PILL_CURRENT else PILL_ACCENT
```

`pillColor` drives pill background (`copy(alpha=0.12f)`), badge fill, label text, and dropdown icon.

**Rule:** Only `PeriodMode.MONTH` matching the current calendar month → `PILL_CURRENT`. All other modes and past/future months → `PILL_ACCENT`.

## ADR-054: Overview Bar Chart — Stacked Category Colors + Thinner Bars (2026-06-01)

**Problem:** `SpendingChart` drew each day's bar as a single solid `accentColor` (pink/teal). The chart gave no visual breakdown of which categories drove spending on a given day.

**Changes:**

### OverviewViewModel.kt — new data model

```kotlin
data class CategorySegment(val colorHex: String, val amount: Double)

data class DayBar(
    val day:      Int,
    val amount:   Double,
    val isFuture: Boolean,
    val segments: List<CategorySegment> = emptyList()   // sorted largest first
)
```

`buildState` now groups each day's transactions by `tx.categoryColor` (from `TransactionWithDetails.categoryColor: String?`) and sorts segments by `amount DESC`. No new DAO query — `monoTx` (already fetched) carries `categoryColor` via the JOIN in `TransactionDao`.

Fallback: transactions without a category (`categoryColor == null`) use `"#9E9E9E"` (grey).

### OverviewScreen.kt — SpendingChart

**Thinner bars:** `gap` changed from `slotW * 0.15f` to `slotW * 0.45f` → bar width ~55% of slot (was ~85%).

**Stacked drawing:** Replaced `drawRect(accentColor, ...)` with a loop from bottom to top:
```kotlin
var currentBottom = h
segments.forEach { seg ->
    val segColor = Color(parseColor(seg.colorHex))  // or accentColor on parse failure
    val segH   = (h - barTop) * (seg.amount / bar.amount).toFloat()
    drawRect(segColor, topLeft = Offset(barL, currentBottom - segH), size = Size(barW, segH))
    currentBottom -= segH
}
```
Largest segment at the bottom; smaller segments stack upward. Income mode / empty segments → fallback single `accentColor` bar.

**Rule:** `DayBar.segments` are sorted largest-first by the ViewModel. `SpendingChart` draws them in order → the first segment is always the bottom one. Do not sort in `SpendingChart`.

**Rule:** `SpendingChart` does not re-parse category color from `OverviewCatRow`. It uses `seg.colorHex` already stored in `DayBar.segments`. These are the raw hex strings from `CategoryEntity.colorHex` via the DAO JOIN — always `#RRGGBB` format.

## ADR-055: Sentry Enabled In All Environments (2026-06-01)

Sentry captures events in both `debug` and `production` builds. Environment label distinguishes them in the dashboard.

**Configuration (`onemoneyApp.kt`):**
```kotlin
options.isEnabled   = true          // explicit — not just default
options.environment = if (BuildConfig.DEBUG) "debug" else "production"
options.sampleRate  = 1.0           // 100% error events
options.tracesSampleRate = 1.0      // 100% performance traces
options.isDebug     = BuildConfig.DEBUG  // verbose SDK logs in debug only
```

**Rule:** Do not add `if (BuildConfig.DEBUG) return` or `options.isEnabled = false` in debug builds. Capturing debug-environment events is intentional for development feedback.

**Dashboard tip:** In Sentry UI → Environment filter, select "All Environments" or "debug" to see development events. Default filter is often "production" only.

## ADR-056: repairDefaultColors() — Canonical Root Category Colors On Startup (2026-06-01)

`CategoryRepository.repairDefaultColors()` enforces canonical `colorHex` values for the 9 default root expense categories on every app start. Called from `onemoneyApp.seedInitialData()` alongside `repairIconKeys()`.

**Why:** `seedDefaults()` runs only when the category table is empty. Users who imported data from a backup or ran the app before seed colors were locked end up with different colors. `repairDefaultColors()` fixes this idempotently.

**Canonical colors:**
| Category | colorHex |
|---|---|
| Продукти | `#4AAFE8` |
| Ресторація | `#4659BE` |
| Дозвілля | `#F73579` |
| Транспорт | `#FFA834` |
| Здоров'я | `#48B456` |
| Подарунки | `#F34B4D` |
| Сім'я | `#7A48F2` |
| Покупки | `#7B5947` |
| Робота | `#1565C0` |

**Filter:** matches ALL root categories (`parentId == null`) by name — does NOT filter by `isDefault`. Reason: imported categories may have `isDefault = false` even if they are the canonical roots.

**Apostrophe normalization:** keys use standard `'` — the filter normalizes `ʼ` and `` ` `` variants before lookup.

**Rule:** To change a canonical root color, update both `seedDefaults()` and the `canonical` map in `repairDefaultColors()`. They must agree.

## ADR-057: BackupSerializer — Defensive Null Handling And creditLimit Field (2026-06-01)

Two bug fixes to `util/BackupSerializer.kt`:

**1. Nullable field null-check pattern:**

Old (broken for old backups without the key):
```kotlin
toAccountId = if (t.isNull("toAccountId")) null else t.getLong("toAccountId")
```
`JSONObject.isNull(key)` returns `false` when the key is absent entirely — then `getLong()` throws `JSONException`.

New (defensive):
```kotlin
toAccountId = if (!t.has("toAccountId") || t.isNull("toAccountId")) null else t.getLong("toAccountId")
categoryId  = if (!t.has("categoryId")  || t.isNull("categoryId"))  null else t.getLong("categoryId")
```

**Rule:** For any nullable Long field in BackupSerializer, always check `has()` before `isNull()` before `getLong()`.

**2. creditLimit serialization:**

`AccountEntity.creditLimit: Double` was not serialized before this fix. On export it was silently dropped; on import it defaulted to `0.0`.

Fix: added `put("creditLimit", a.creditLimit)` in `serialize` and `creditLimit = a.optDouble("creditLimit", 0.0)` in `deserialize`.

**Rule:** When adding a new field to any entity, update both `serialize` and `deserialize` in `BackupSerializer`. Use `opt*` methods in `deserialize` for backward compatibility with old backups.

## ADR-058: SharedMonthNavPill Redesign — Icons, Typography, Text Case (2026-06-01)

**Changes to `ui/main/SharedMonthPill.kt`:**

| Element | Before | After |
|---|---|---|
| Nav arrows icon | `KeyboardDoubleArrowLeft/Right` (double «») | `KeyboardArrowLeft/Right` (single ‹›) |
| Nav arrows tint | `onSurface.copy(alpha=0.45f)` (gray) | `pillColor` (matches pill accent) |
| Nav arrows size | 32dp | 28dp |
| Month label font | `titleSmall + Bold` | `bodyLarge + SemiBold` |
| Month label text | `MONTH_NAMES_UA` (UPPERCASE) | `MONTH_NAMES_UA_FULL` (Title Case) |
| TODAY/WEEK/DAY/RANGE | `.uppercase()` applied | `.uppercase()` removed |
| "ВІД ПОЧАТКУ" | uppercase | "Від початку" |
| Dropdown icon | `ArrowDropDown` (triangle ▾) | `ExpandMore` (thinner chevron ∨) |

**Changes to `ui/main/MainScreen.kt` — SharedTopBar:**

| Element | Before | After |
|---|---|---|
| Balance font | `titleLarge + Bold` | `headlineMedium + Bold` |
| User icon | `Icons.Outlined.Person` | `Icons.Filled.Person` |

**Rule:** Nav arrows use `pillColor` (which is `PILL_CURRENT` for current month, `PILL_ACCENT` otherwise) — they always match the pill.

**Rule:** `PeriodMode.MONTH` applies `.uppercase()` to month name → "ТРАВЕНЬ 2026". All other modes (TODAY, WEEK, DAY, RANGE, ALL, YEAR) do NOT apply `.uppercase()` — their strings are already formatted correctly. Do not apply `.uppercase()` globally in `pillLabelFor()`.

## ADR-059: Generic Icon Normalization At All Import/Repair Points (2026-06-01)

**Problem:** Categories imported from MonoFlow sync or backup restore could arrive with `icon = "family"` for specific functional categories (Зв'язок, Інтернет, Комунальні). Since `"family"` is a valid key in `validKeys`, `repairIconKeys()` skipped these rows — the wrong icon persisted until a new data migration was written. This was a whack-a-mole cycle: bug → migration → nameOverride → bug reappears after next import.

**Root cause analysis:**
- `MonoFlowSyncWorker.doWork()` called `categoryDao.insertCategories(data.categories)` with no normalization — raw categories from server bypassed all icon logic.
- `normalizeImportedCategory()` in `DataViewModel` only ran `suggestCategoryStyle()` for `icon == "category"`. A category with `icon = "family"` was returned as-is.
- `repairIconKeys()` only called `suggestCategoryStyle()` for `icon !in validKeys`. `"family"` is valid → skipped.

**Fix (three synchronized points):**

**1. `MonoFlowSyncWorker`** (`workers/MonoFlowSyncWorker.kt`): now normalizes categories before insert:
```kotlin
val normalizedCats = data.categories.map { cat ->
    if (cat.icon in setOf("category", "family")) {
        val (suggested, color) = suggestCategoryStyle(cat.name, cat.type)
        if (suggested != "category" && suggested != cat.icon) cat.copy(icon = suggested, colorHex = color)
        else cat
    } else cat
}
ep.categoryDao().insertCategories(normalizedCats)
```

**2. `DataViewModel.normalizeImportedCategory()`** (`ui/data/DataViewModel.kt`): extended to handle `"family"` alongside `"category"`:
```kotlin
if (cat.icon in setOf("category", "family")) {
    val (suggested, color) = suggestCategoryStyle(cat.name, cat.type)
    if (suggested != "category" && suggested != cat.icon) return cat.copy(icon = suggested, colorHex = color)
}
```

**3. `CategoryRepository.repairIconKeys()`** (`data/repository/CategoryRepository.kt`): added generic-icon step between nameOverrides and invalid-key check:
```kotlin
val genericIcons = setOf("category", "family")
val newIcon = when {
    forced != null && forced != cat.icon -> forced
    cat.icon in genericIcons -> {
        val suggested = suggestCategoryStyle(cat.name, cat.type).first
        if (suggested != "category" && suggested != cat.icon) suggested else cat.icon
    }
    cat.icon !in validKeys -> suggestCategoryStyle(cat.name, cat.type).first
    else -> cat.icon
}
```

**Why "family" as generic:** `"family"` is the correct icon for "Сім'я" — `suggestCategoryStyle("сім'я", ...)` returns `"family"`, so it is left unchanged. For "Зв'язок", it returns `"phone"` → override applied. The check `suggested != cat.icon` prevents no-ops.

**Migration 28→29** (`AppDatabase.kt`): unconditional SQL backfill for rows already in the DB with wrong icons from previous sync cycles:
```sql
UPDATE categories SET icon='home',  colorHex='#546E7A' WHERE LOWER(TRIM(name)) LIKE '%комунал%'
UPDATE categories SET icon='phone', colorHex='#3F51B5' WHERE LOWER(TRIM(name)) LIKE '%зв%язок%'
UPDATE categories SET icon='wifi',  colorHex='#00BCD4' WHERE LOWER(TRIM(name)) = 'інтернет'
```

**Rule:** `"category"` and `"family"` are the two generic icons that may be wrong for specific-purpose categories. Do not add other icons to `genericIcons` without verifying that `suggestCategoryStyle` returns a more specific result for all affected names.

**Rule:** The three normalization points (MonoFlowSyncWorker, normalizeImportedCategory, repairIconKeys) must handle the same `genericIcons` set. If a new generic icon is identified, update all three.

## ADR-067: Move "+" to Grid, Center Donut Text, Font Refinements (2026-06-01)

**Problem:** Multiple visual follow-up issues after ADR-066:
1. `AddCategoryChip` floated inside `DonutChart` at `BottomCenter` — felt disconnected from the grid.
2. Donut center text had `offset(y = -16.dp)` to make room for the "+" → text was not centered in the donut ring.
3. Category chip text (title 10sp, amounts 9sp) was too small.
4. Month pill text (badge + label 13sp) was too large.
5. Pill bg `#E7E7F2` appeared too grey — user preferred `#D5D6EC` (more saturated lavender).

**Decisions:**

**Move "+" to grid** (`CategoriesScreen.kt`, `CategoriesWidgets.kt`):
- Removed `onAdd: (() -> Unit)?` param from `DonutChart`.
- Added `item(key = "add_row")` in `CategoriesGridContent` after `extCats` loop — `AddCategoryChip` in leftmost slot of a Row with same `chipW`/`chipHeight`/`rowPad`/`chipGap` as all other rows.

**Rule:** `AddCategoryChip` must always live in the grid, never inside `DonutChart`. `DonutChart` has no `onAdd` parameter.

**Center donut text** (`CategoriesWidgets.kt`): removed `.offset(y = (-16).dp)` from center `Column`.

**Font refinements** (`OneMoneyTokens.kt`, `SharedMonthPill.kt`):
- `categoryTitle`: 10sp → 11sp / lineHeight 12→13sp
- `categoryTopAmount` / `categoryBottomAmount`: 9sp → 10sp / lineHeight 11→12sp
- Pill badge + label: 13sp → 11sp (`FontWeight.Light`)
- Pill bg for current month: `#E7E7F2` → `#D5D6EC`

**Rule:** Do not go below 11sp for category names or below 10sp for category amounts.

## ADR-066: Grid Alignment, Blue Current-Month Pill, Lighter Nav/Pill Text (2026-06-01)

**Problem:** Four visual issues after ADR-065:
1. Top-row chips were horizontally offset from mid-section side columns — caused by `chipW` having an upper bound (`coerceIn(68, 86)`). On phones wider than 370dp the chips clamped to 86dp, `Arrangement.spacedBy(6.dp, CenterHorizontally)` added centering offset, while the mid-section Columns had no such offset.
2. Month pill always red even when showing the current month.
3. Bottom nav active label was `FontWeight.Medium` → looked heavy.
4. Month/year text in pill was `FontWeight.Medium` → looked bold.

**Decisions:**

**Grid alignment** (`CategoriesScreen.kt`):
```kotlin
// was: .coerceIn(68.dp, 86.dp)  — capped at 86dp on wide phones
// now: .coerceAtLeast(68.dp)    — no upper bound; chipW fills row exactly
```
With no upper bound, `4*chipW + 3*6dp == maxWidth - 8dp` always, so `CenterHorizontally` has no offset and top-row chips align perfectly with mid-section side columns.

**Rule:** Never add an upper bound to `chipW`. If chips feel too wide on tablets, handle via a tablet-specific layout, not by capping `chipW`.

**Blue pill for current month** (`SharedMonthNavPill.kt`):
```kotlin
val isCurrentMonth = appMonth.mode == PeriodMode.MONTH &&
    appMonth.month == today.get(Calendar.MONTH) &&
    appMonth.year  == today.get(Calendar.YEAR)
val pillColor = if (isCurrentMonth) Color(0xFF2196F3) else MonthRed
val pillBg    = if (isCurrentMonth) Color(0xFFD4E8FF) else MonthRedLight
```
All pill elements (left arrow, badge border, badge text, label, chevron) use `pillColor`. Right arrow is always `Color(0xFF111111)`.

**Rule:** Non-current periods (past months, other modes) always use `MonthRed`. Only `PeriodMode.MONTH` at current calendar month+year gets the blue treatment.

**Lighter text:**
- Bottom nav labels: `fontWeight = FontWeight.Normal` for all tabs (removed Medium for active tab).
- Month pill badge + label text: `fontWeight = FontWeight.Normal` (was Medium).

## ADR-065: Category Chip Width Formula + Title Weight Fix (2026-06-01)

**Problem:** Two visual bugs remained after ADR-064:
1. `categoryTitle` was `11sp Medium` — appeared too bold on device.
2. Top-row chips overlapped each other — `chipW` was computed from `(maxWidth - hPad*2) / 4` which didn't account for `CategoryGridRow`'s own `padding(horizontal = 4.dp)` and `Arrangement.spacedBy(6.dp)` (3 gaps × 6dp = 18dp). A 360dp screen gave `chipW = 82dp`, but 4×82+3×6 = 346dp > 344dp available → chips overflowed.

**Decision:**

`OneMoneyTokens.kt` — `categoryTitle`: `11sp Medium` → **`10sp Normal`**

`CategoriesScreen.kt` — `chipW` formula inside `BoxWithConstraints`:
```kotlin
// OLD (wrong — didn't subtract row padding or gaps):
val topRowCellW = (maxWidth - hPad * 2) / 4
val chipW = topRowCellW.coerceIn(72.dp, 92.dp)

// NEW (correct):
val rowPad  = 4.dp   // CategoryGridRow padding(horizontal=4dp)
val chipGap = 6.dp   // spacedBy(6dp), 3 gaps for 4 chips
val chipW   = ((maxWidth - rowPad * 2 - chipGap * 3) / 4).coerceIn(68.dp, 86.dp)
```

**Rule:** Any change to `CategoryGridRow`'s horizontal padding or `Arrangement.spacedBy` value must also update the `chipW` formula in `CategoriesGridContent`. The two must stay in sync.

## ADR-064: Categories Grid Adaptive Sizing + Visual Overhaul v2 (2026-06-01)

**Problem:** After the initial token refactoring, the grid used static `CHIP_WIDTH/HEIGHT` constants that didn't adapt to device screen width. Circle backgrounds were too saturated. DonutChart ring was too thick with no inner white fill. Several global token values (`centerRing`, `topBarLabel/Balance`, `BottomNavActivePill`) were still incorrect.

**Decision:**

**Adaptive grid** — `CategoriesGridContent` now wraps its layout in `BoxWithConstraints`. Three values are computed at runtime:
```kotlin
val hPad       = when { maxWidth < 360.dp → 12.dp; < 420.dp → 16.dp; else → 20.dp }
val chipW      = ((maxWidth - hPad * 2) / 4).coerceIn(72.dp, 92.dp)
val circleSize = when { maxWidth < 360.dp → 46.dp; < 420.dp → 50.dp; else → 54.dp }
val chipHeight = when { maxHeight < 700.dp → 92.dp; < 800.dp → 100.dp; else → 108.dp }
```
These are threaded through `CategoryGridRow(chipWidth, chipHeight, circleSize)` → `CategoryGridSlot(chipWidth, chipHeight, circleSize)` → `CategoryChip(overrideWidth, overrideHeight, overrideCircle)`. Static constants (`CHIP_WIDTH` etc.) are kept as compile-time defaults for backward-compat callers.

**Circle backgrounds** — `CategoryScreenTokens.byName` colors lightened ~20% toward white (lerp factor 0.20). Fallback alpha: `hasBudget` 0.28f → **0.22f**, no-budget 0.13f → **0.10f**.

**DonutChart** — stroke `minDim * 0.04f` (was 0.045f). White filled circle drawn before ring arcs for clean inner background. Ring color `#E6E6EB` (via `OneMoneyTheme.colors.centerRing`).

**Token corrections:**

| Token | Was | Now |
|-------|-----|-----|
| `centerRing` | #E3E3E8 | **#E6E6EB** |
| `BottomNavActivePill` | #E3E4F2 | **#E7E7F2** |
| `topBarLabel` fontSize | 12sp | **13sp** |
| `topBarBalance` fontSize/weight | 20sp Bold | **18sp Normal** |
| `SharedMonthPill` right chevron | `MonthRed` | **`Color(0xFF111111)`** |
| `CategoryScreenTokens` all `circleBg` | original | **+20% lightened** |

**Rules:**
- Do not hardcode `chipW/chipH/circleSize` in `CategoriesGridContent` — always compute via `BoxWithConstraints`.
- `CategoryChip`'s `overrideWidth/Height/Circle` params must be passed from the grid — do not rely on token defaults for the categories grid layout.
- Donut inner white fill must be drawn before arc strokes, not after.

## ADR-062: Global Visual Lightening — White Background + Reduced Typography (2026-06-01)

**Problem:** The app background was `#E1DEE2` (grey-pink), font sizes were too large, and the bottom nav was heavy. The reference design uses a white background with lighter typography.

**Decision:** Several global tokens updated in `Theme.kt`:

| Token | Was | Now |
|-------|-----|-----|
| `md_light_background` | #E1DEE2 | **#FFFFFF** |
| `MonthRed` | #C81A19 | **#D7261E** |
| `MonthRedLight` | #F6D3CF | **#F8D9D5** |
| `BottomNavBg` | #DFDCDF | **#FFFFFF** |
| `BottomNavActivePill` | #D8D7E7 | **#E7E7F2** (updated again in ADR-064) |

`MainScreen.kt` — `SharedTopBar`:
- "Всі рахунки": 16sp → **14sp Medium**, color `#2F3442`
- Balance: 26sp → **20sp Normal**, color `#2F3442`
- Bottom nav label: 13sp → **11sp** (Medium active, Normal inactive)
- Nav colors: selected **#2F3442**, unselected **#444857**

`SharedMonthPill.kt`:
- Badge border: 1.5dp → **1dp**
- Badge text: 15sp → **14sp**
- Month text: 17sp → **14sp**
- Right chevron: `#292F3F` → `MonthRed`

**Rule:** Do not revert `md_light_background` to grey — the reference design is white. If a specific screen needs a non-white background, apply it locally with `Modifier.background(...)`, not by changing the global Material token.

## ADR-061: Categories Screen Design Token System (2026-06-01)

**Problem:** `CategoriesWidgets.kt` contained ~40 hardcoded `Color(0x...)`, dozens of hardcoded `.dp`/`.sp`/`FontWeight` values, and a local `categoryVisualOverride` map inside composable functions. Visual changes required hunting through composable bodies.

**Decision:** Centralise all visual constants into three new files in `ui/theme/`:

| File | Contents |
|------|----------|
| `OneMoneyTokens.kt` | `OneMoneyColors`, `OneMoneyTypography`, `OneMoneyDimens` data classes + `OneMoneyLightTokens` object with all values |
| `CategoryScreenTokens.kt` | `CategoryVisualStyle(circleBg, iconTint)` + `CategoryScreenTokens.byName` map (9 root categories) + `resolve(name, type, fallback, hasBudget)` |
| `OneMoneyTheme.kt` | `LocalOneMoneyColors/Typography/Dimens` CompositionLocals, `OneMoneyTheme` accessor object, `OneMoneyThemeProvider` |

`OneMoneyThemeProvider` wraps the app root inside `onemoneyTheme {}` in `MainActivity`.

`CategoriesWidgets.kt` was rewritten to use `OneMoneyTheme.*` and `CategoryScreenTokens.resolve()` instead of all hardcoded values.

**Token values (as of 2026-06-01):**
- `categoryTitle`: Medium 12sp — `categoryTopAmount`/`categoryBottomAmount`: Normal 11sp
- `centerTitle`: Medium 16sp — `centerAmount`: Normal 16sp
- `subcategoryTitle`: Normal 10sp — `subcategoryAmount`: Normal 9sp
- `addButtonStroke` #D8D8D8 — `addButtonIcon` #B6B6B6 — `centerRing` #DEDEE3
- Donut stroke: `minDim * 0.055f` (was 0.09f)

**Rules:**
- Do not add hardcoded `Color(0x...)`, `.sp`, or `FontWeight` directly inside `CategoriesWidgets.kt` — add a named token in `OneMoneyTokens.kt` first.
- `CategoryScreenTokens.byName` is the single source for per-category circle/icon colours. Do not add a second local map anywhere.
- `FontWeight.Bold` and `FontWeight.SemiBold` are banned on the categories screen. Only `Normal` and `Medium` are permitted (budget over-run uses `Medium`).

## ADR-060: ModalNavigationDrawer — gesturesEnabled = drawerState.isOpen (2026-06-01)

**Problem:** `gesturesEnabled = false` on `ModalNavigationDrawer` disabled not only swipe gestures but also scrim tap (click-outside-to-dismiss) in certain Material3 versions. Users could only close the drawer via the explicit ✕ button.

**Decision:** `gesturesEnabled = drawerState.isOpen`.

- Closed → gestures off → no accidental open from swiping inside content.
- Open → gestures on → scrim tap and swipe-to-close both work normally.
- Added `BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }` as the highest-priority back handler so the Android back button also closes the drawer.

## ADR-063: App-Wide Design Token System — No Magic Numbers in UI Files (2026-06-01)

**Problem:** ~50 UI files contained `Color(0xFF…)` literals, bare `8.dp/16.dp/24.dp` values, and hardcoded `sp` values. Visual or spacing changes required grep-and-replace across dozens of files, with high regression risk.

**Decision:** Extended `OneMoneyTokens.kt` with three token classes (`OneMoneyColors`, `OneMoneyTypography`, `OneMoneyDimens`) covering the full app, not just the category screen. Applied `Spacing.*` (from `Spacing.kt`) and `OneMoneyTheme.{colors,dimens,typography}` across all UI composables.

**Token files (ui/theme/):**
- `OneMoneyTokens.kt` — data classes + `OneMoneyLightTokens` object with all values
- `OneMoneyTheme.kt` — `CompositionLocal` provider; access via `OneMoneyTheme.colors/dimens/typography`
- `Spacing.kt` — `xs=4dp sm=8dp md=12dp lg=16dp xl=20dp xxl=24dp`
- `CategoryScreenTokens.kt` — category-specific color mappings (per-name overrides)

**Spacing rule:** Replace `4.dp/8.dp/12.dp/16.dp/20.dp/24.dp` with `Spacing.xs/sm/md/lg/xl/xxl`. Values outside this set (6dp, 10dp, 14dp, etc.) stay as raw dp — they are layout-specific.

**Color rule:** No bare `Color(0xFF…)` in UI composable files except:
- Fallback colors for user-supplied hex (e.g., `Color(0xFFFF5722)` as default category color)
- Screen-specific palette constants declared as `private val : Color` at file top (see pattern below)

**`private val` pattern for screen-specific colors:**
When a file has palette colors that are not in the global token system (action button tints, account type colors, filter chip colors, etc.), declare them as typed private vals at the top of the file, immediately after imports:
```kotlin
private val FallbackAccountColor: Color = Color(0xFF4361EE)
private val ActionAmberColor:     Color = Color(0xFFFFAB00)
private val AccountTypeDebtColor: Color = Color(0xFF2E7D60)
```
Always add `: Color` explicitly — Kotlin type inference fails with a "recursive problem" error on top-level Color vals in some Compose contexts. Applied in: `AccountsScreen.kt`, `AccountSheets.kt`, `AccountPickerSheets.kt`, `IconColorPickerScreen.kt`, `TransactionsListScreen.kt`, `MainScreen.kt`, `BudgetScreen.kt`, `BudgetSheets.kt`, `CategoriesScreen.kt`, `CategoriesWidgets.kt`, `CategorySheets.kt`, `CategoryFormSheets.kt`, `CalcKeypad.kt` (`CalcConfirmColor=#4CAF50`), `CalcDateSheet.kt` (`FallbackAccountColor=#3949AB`, `DarkOnLightColor=#1C1B1F`).

**Dimension rule:** Component sizes (`44.dp` avatar, `24.dp` standard icon, etc.) use `OneMoneyTheme.dimens.*`. Do not hardcode these in composable bodies.

**Rule:** All new UI composables must use `OneMoneyTheme.{colors,dimens,typography}` and `Spacing.*` from the first line. Never add a new local constant for a value already in the token system.

## ADR-067: Root Category Sort Order — One-Time Migration, User-Controlled After (2026-06-01)

**Problem:** Users who had the app installed before `sortOrder` was correctly seeded, or who restored a backup, could have root expense categories in the wrong display order (DB row insertion order instead of the intended grid order).

**Decision:** DB migration 29→30 sets the canonical `sortOrder` for the 9 root expense categories exactly once. After the migration runs, `sortOrder` is fully user-controlled — never reset on startup.

**Canonical order:**
| sortOrder | Category |
|---|---|
| 1 | Продукти |
| 2 | Ресторація |
| 3 | Дозвілля |
| 4 | Транспорт |
| 5 | Здоров'я |
| 6 | Подарунки |
| 7 | Сім'я |
| 8 | Покупки |
| 9 | Робота |

**Migration SQL** (uses `LIKE 'Здоров%'` / `LIKE 'Сім%'` for apostrophe-agnostic matching; `AND parentId IS NULL AND type = 'EXPENSE'` guard):
```sql
UPDATE categories SET sortOrder = 1 WHERE name = 'Продукти'   AND parentId IS NULL AND type = 'EXPENSE'
-- ... (one UPDATE per category)
UPDATE categories SET sortOrder = 5 WHERE name LIKE 'Здоров%' AND parentId IS NULL AND type = 'EXPENSE'
UPDATE categories SET sortOrder = 7 WHERE name LIKE 'Сім%'    AND parentId IS NULL AND type = 'EXPENSE'
```

**Rejected alternative:** `repairDefaultOrder()` called on every startup — this would reset any user reordering (drag-and-drop in `EditCategoriesScreen`) on the next app launch, defeating the purpose of user control.

**Rule:** Do NOT add a `repairDefaultOrder()` startup function. The sort order for default categories is fixed by migration 29→30 and is user-controlled thereafter. If the canonical order needs to change, add a new migration — do not reset on every startup.

## ADR-068: DonutChart Center Text — All Light (300) Weight (2026-06-01)

**Problem:** "Витрати"/"Доходи" label and the expense/income amounts in the DonutChart center appeared too heavy visually. Label was already `FontWeight.Light` (300); amounts were `FontWeight.Normal` (400), creating weight inconsistency between the label and the numbers below it.

**Decision:** Change `centerAmount` in `OneMoneyTokens.kt` from `FontWeight.Normal` to `FontWeight.Light`. All three text elements in the donut center (label, expense total, income total) now use Light (300).

**Token values:**
| Token | fontSize | lineHeight | Weight |
|---|---|---|---|
| `centerTitle` | 15sp | 18sp | Light 300 |
| `centerAmount` | 15sp | 18sp | Light 300 |

**Rule:** Both `typo.centerTitle` and `typo.centerAmount` must stay at `FontWeight.Light` (300). Do not raise to Normal or Medium — the center of the ring is a small, decorative area where heavy weight reads as cluttered.

## ADR-069: SharedMonthNavPill Arrow Colors — Left Always Dark, Right Follows pillColor (2026-06-01)

**Problem:** When viewing a past month (e.g. May while current month is June), the LEFT `<<` arrow was red and the RIGHT `>>` arrow was black. Semantically the right arrow is the "return to current month" direction, so it should be highlighted, not the left arrow.

**Root cause (before this ADR):**
- Left arrow used `tint = pillColor` — red for past months
- Right arrow used `tint = Color(0xFF111111)` — always black

**Decision:**
- Left arrow: `tint = Color(0xFF111111)` always (neutral, no semantic meaning for direction)
- Right arrow: `tint = if (isCurrentMonth) Color(0xFF111111) else MonthRed` (matches `pillColor`)

```kotlin
Icon(DoubleChevronLeft,  tint = Color(0xFF111111), ...)             // always dark
Icon(DoubleChevronRight, tint = if (isCurrentMonth) Color(0xFF111111) else MonthRed, ...)
```

**Rule:** Left navigation arrow is always `Color(0xFF111111)`. Right arrow uses `pillColor` logic — red when not on current month, dark when on current month. This makes the right arrow a visual "return to current" cue.

**Rule:** Do not make both arrows follow `pillColor` — a red left arrow has no semantic purpose and clutters the UI when in a past month.

## ADR-070: Правки операций уезжают на mono-flow отдельной очередью (2026-09-07)

**Problem:** `MonoFlowSyncWorker` тянет `/api/sync` и делает `INSERT OR REPLACE` по id.
Любая локальная правка операции, пришедшей с сервера (переименование, смена категории),
выглядела применённой ровно до следующего пробуждения воркера, после чего молча
затиралась серверной версией.

**Decision:** Синхронизация становится двусторонней.

- На сервере (`mono-flow`) заведена таблица `miq_overrides` и `POST /api/tx/override`
  (контракт — `iface.mono-flow`). Правка хранится ОТДЕЛЬНО от транзакции, потому что
  строки транзакций пересобираются из данных Монобанка при каждой отдаче, и накладывается
  в `build_moneyiq_json`.
- В приложении заведена таблица `tx_overrides` (migration 30→31) — очередь правок, и
  `TxOverridePushWorker`, который её дожимает. Правка переживает офлайн: `synced`
  снимается только после ответа сервера.
- `MonoFlowSyncWorker` СНАЧАЛА отдаёт очередь и только потом тянет данные.

**Rule:** Категория адресуется на сервер ИМЕНЕМ, не id: id категории на сервере
производится от имени (`_sid("cat", name)`), а локальный id к серверному отношения не имеет.

**Rule:** Всё, что правится локально и приходит с сервера, обязано иметь путь обратно.
Правка без пути назад — это не фича, а отложенная потеря данных.

**Rule:** Операции, заведённые в приложении вручную (id < 10^12), на сервер не уезжают —
он их не знает. Это логируется, а не пропускается молча.

## ADR-071: Синк не тянет данные, пока не отдал свои правки (2026-09-07)

**Problem:** Приёмка ADR-070 показала дыру: `MonoFlowSyncWorker` откладывал тягу только
при сетевой ошибке, а ручной «Sync now» (`DataViewModel.syncFromMonoFlowNow`) пушем вообще
не занимался. В обоих случаях `INSERT OR REPLACE` по id накатывал серверную версию поверх
правки, которую сервер ещё не принял, — правка исчезала без следа.

**Decision:** Тяга данных не начинается, пока очередь правок не пуста — и в фоновом
воркере, и в ручном синке. Ручной синк в этом случае говорит об этом пользователю
(`data_monoflow_pending_edits`), фоновый возвращает `retry`.

**Защита от вечного простоя:** правка, которую сервер отвергает, копит `attempts`. После
`MAX_ATTEMPTS = 5` она перестаёт блокировать синк, но остаётся в таблице с `lastError`, и
факт пишется в лог уровнем `error`. Временные отказы (401/403/408/429, 5xx) и сетевые сбои
попытками НЕ считаются: протухший токен не должен «убивать» правильную правку.

**Rule:** `MonoFlowSyncWorker` больше не глушит исключение молча — провал синка пишется в
лог с типом и сообщением. Синк, упавший беззвучно, снаружи неотличим от «новых данных нет».

**Проверено на эмуляторе (Small_Phone_API_33, стенд вместо боевого mono-flow):** при
сервере, отвечающем 500 на правку, приложение не сделало ни одного GET; как только сервер
начал принимать — сначала ушёл POST, затем GET.

## ADR-072: HTTP разрешён только debug-сборке и только для локального стенда (2026-09-08)

**Problem:** С targetSdk 36 cleartext-трафик запрещён по умолчанию, поэтому приложение на
эмуляторе не могло ходить в локальный стенд mono-flow (`http://10.0.2.2:8765`) — синк
падал с «Cleartext HTTP traffic to 10.0.2.2 not permitted». Проверять двусторонний синк
оставалось только на БОЕВОМ mono-flow с настоящим `SYNC_TOKEN`, то есть тестовые правки
уезжали бы в живые данные Сержа.

**Decision:** `app/src/debug/AndroidManifest.xml` подключает
`app/src/debug/res/xml/network_security_config.xml`, где HTTP разрешён ровно для трёх
адресов: `10.0.2.2` (хост-машина с точки зрения эмулятора), `localhost`, `127.0.0.1`.

**Rule:** Файл живёт ТОЛЬКО в debug-source-set. Релизная сборка его не видит и по-прежнему
ходит исключительно по HTTPS — проверяется тем, что в `app/src/main/AndroidManifest.xml`
нет `networkSecurityConfig`, а `tools:replace` стоит в debug-манифесте.

**Rule:** Стенд (`fake_monoflow.py`) с токеном `test` — единственный допустимый адресат
проверок синка. Боевой `SYNC_TOKEN` в эмулятор не вводится: однажды это уже привело к
тому, что тестовая правка осталась в боевой базе mono-flow.

## ADR-073: Зачисленная сумма перевода хранится в операции (2026-09-08)

**Problem:** Перевод между счетами в разных валютах прибавлял счёту-получателю ту же
цифру, что списывал с источника: 900 ₴ приезжали на долларовый счёт как 900 $
(`TransactionRepository`, все три пути — add/update/delete). Дефект жил с самого начала и
всплыл при проверке перемещения на IBKR (serg/tasks#721).

**Decision:** Сумма пересчитывается по кешу курсов НБУ (`CurrencyRatesRepository`,
`rate` = сколько гривен стоит единица валюты) и **сохраняется в самой операции** —
`transactions.toAmount` (миграция 33→34, nullable).

**Rule:** Откат операции берёт `toAmount`, а не считает по текущему курсу. Курс меняется;
пересчёт при удалении оставил бы на счетах разницу, которую никто не заметит. `null`
читается как «столько же, сколько списано» — так ведут себя все операции до миграции.

**Rule:** Пересчёт делается заново только если поменялись сумма или счёт-получатель.
Правка заметки у старого перевода не должна двигать балансы.

**Rule:** Курс неизвестен (кеш пуст, валюты нет у НБУ) — зачисляем «как есть» И пишем
`Log.w` с обеими валютами и id счетов. Молчаливое 1:1 — это ровно тот дефект, который
чинится: неточность обязана быть видна не только в балансе.

**Проверено:** 6 unit-тестов в `TransactionRepositoryTest` — конвертация UAH→USD по курсу
41 (820 ₴ → 20 $), отсутствие конвертации внутри одной валюты, сохранение `toAmount` в
операции, откат по сохранённому значению при изменившемся курсе (50 вместо 41),
зачисление «как есть» при неизвестном курсе, конвертация при перекласификации расхода в
перемещение. Всего 257 тестов, 0 падений.

## ADR-074: Белый цвет — только внутри кружка иконки (2026-09-08)

**Problem:** Сумма под иконкой категории красилась в `iconTint`, а он для всех категорий
вне захардкоженного `CategoryScreenTokens.byName` (9 имён) равен `Color.White`. Белый
уместен ВНУТРИ цветного кружка, но на светлой карточке сумма становилась белой по белому
и не читалась вовсе. Задевало все подкатегории и всё, что приезжает с сервера, — то есть
у Сержа большинство категорий. В тёмной теме дефект не виден, поэтому жил с 01.06.2026
(serg/tasks#745).

**Decision:** У суммы свой цвет — `amountTint`, считается отдельно от `iconTint`:
нет трат → `secondaryText`; категория с токенами → её `iconTint`; тёмная тема →
`Color.White`; иначе → собственный цвет категории (`colorHex`).

**Rule:** `Color.White` разрешён только там, где под ним гарантированно цветная подложка
(кружок иконки, бейдж). Текст на поверхности карточки берёт цвет из темы или из категории.
Проверять надо ОБЕ темы: половина цветовых дефектов не видна в той, которой пользуешься.

**Проверено на эмуляторе (замер пикселей области под иконкой «Авто», spending = 900):**
светлая тема — `(251,140,0)` = `#FB8C00`, цвет категории (до правки приёмщик намерил
сплошной `(255,255,255)`); тёмная тема — белый на `(18,19,24)`, вид не изменился.

## ADR-075: Падения уезжают в свой GlitchTip, без скриншотов (2026-09-08)

**Problem:** `MoneyIQApp.kt` инициализировал Sentry с DSN **публичного** sentry.io, зашитым
в исходник, и с `isAttachScreenshot = true` + `isAttachViewHierarchy = true`. При любом
падении в чужой SaaS уезжал скриншот экрана с балансами Сержа и дерево вьюх
(serg/tasks#679). Правила меша публичные приёмники запрещают (serg/tasks#678), но здесь
дело даже не в правилах: вложения увозят не текст ошибки, а сами данные.

**Decision (выбор Сержа из трёх вариантов):** приёмник — свой `glitchtip.ibotz.fun`,
проект `mesh/onemoney` (id 24). Вложения выключены. DSN из исходника убран.

**Rule:** DSN живёт в `local.properties` (`glitchtip.dsn`, файл под `.gitignore`) или в
переменной окружения `GLITCHTIP_DSN` для CI, попадает в код через
`BuildConfig.GLITCHTIP_DSN`. В git его нет.

**Rule:** Пустой DSN = Sentry НЕ инициализируется вообще, и об этом пишется в лог.
Инициализация с пустым DSN заставляет SDK молча копить события в очередь.

**Rule:** `isAttachScreenshot` и `isAttachViewHierarchy` остаются `false` даже для своего
приёмника: UI GlitchTip доступен по домену без SSO. `isSendDefaultPii = false`,
`isEnableUserInteractionTracing = false` — имена нажатых элементов рассказывают, что
человек делал с деньгами, а для разбора падения не нужны. Включать что-либо из этого —
только отдельным решением Сержа.

**Проверено событием, а не конфигом** (конфиг доказательством не считается):
- проба с уникальным маркером через `POST /api/24/store/` → HTTP 200, и она нашлась в
  базе GlitchTip как issue 586 `«проверка приёмника onemoney mesh-probe-e68fe1147434»`;
- приложение (debug 1.2.15 на эмуляторе) отдало сессию: в GlitchTip появились
  `Release: onemoney@1.2.15` и `Environment: debug` — то есть события идут в свой
  приёмник, а не в sentry.io;
- настоящее падение (`am crash`) доехало как issue 587
  `RemoteServiceException$CrashedByAdbException`, level 5 — причём в кеш офлайна, а на
  сервер уехало при следующем старте, то есть путь «упал → переоткрыл» тоже работает;
- в данных обоих событий ключей `screenshot`/`attachment` нет.

## ADR-076: Zerno identity and weekly budget allowance (2026-11-04, #2673)

Zerno replaces the visible app brand; applicationId, namespace, package directories,
DB name and backup JSON `app` identifier remain compatible with existing installs.
Default accent seed is `#B07A1A`. The seed selects the exact supplied M3 schemes;
existing explicitly saved accents still use `applyAccent`. Monetary colors resolve
through the existing `OneMoneyTheme.colors` CompositionLocal in both themes.

Weekly budget allowance accrues by calendar days in the selected range:
`storedWeeklyLimit * daysInPeriod / 7`. A seven-day range receives one weekly limit.
`BudgetCatRow.budgetForPeriod` holds the displayed allowance; editing continues to
use the stored `category.budgetAmount`, so saving never multiplies it again.
This does not resolve mixed-currency budget aggregates; that remains open.

Backup replacement validates IDs, references and finite amounts before writing,
then replaces all three financial tables in one Room transaction (`BackupStore`).
Local backups use a JSON snapshot of Room instead of copying an active SQLite file
without its WAL. Publication uses a sibling temporary file, fsync and atomic move.
SAF backups close a staging document then rename; unsupported provider rename fails
visibly and never deletes the preceding backup. Provider-level atomicity cannot be
promised by Android's DocumentsContract and still needs device/provider testing.

Recurring occurrences re-read the due marker inside the same transaction as insertion,
balance updates and clearing the old marker. A stale worker claim cannot consume it twice.
No schema change: Room remains v34; no new columns, tables or migration required.

Validation: independent SQL/XML/contrast checks pass. Kotlin compilation, Room,
Compose and Worker tests have not run due to environment restrictions; see
`zerno-2673-report.md`. This entry records implementation intent, not acceptance.

## ADR-077: Валютні підсумки, personal mono-flow і локальний API Alisaphone (2026-11-08, #2673)

Міжрахункові звіти залишаються у UAH, як їх чинні підписи. Кеш НБУ має rate = UAH
за одиницю валюти. `BaseValuation` не підміняє невідомий курс нульовим успіхом:
частковий підсумок супроводжується missingCurrencies. `TransactionRepository`
конвертує операції **до** агрегації категорій, доходів, витрат і графіків; оригінальні
суми для редагування залишаються у валюті рахунку. Загальне попередження оболонки
перелічує валюти без курсу. Пікери/віджет без власного джерела курсів показують
окремі валютні підсумки. Баланси на межах періоду відновлюються від поточного
залишку з відкатом операцій після межі, включно зі збереженим toAmount переказу.
Прогноз за темпом витрат відсутній без фактичного або бюджетного доходу.
Початковий рахунок отримує локалізоване ім'я з ресурсів і UAH; існуючі дані не мігрують.

`BuildConfig.MONOFLOW_ENABLED` — true лише personal. Play не показує MonoFlow UI,
не планує/не виконує його воркери і не накопичує серверні override. DEBUG_MONOFLOW_*
існують лише в personal; їх значення заповнюються тільки для personalDebug через
variant API, personalRelease має порожні значення. Default credentials належать
flavor source set. Порожня конфігурація → видиме «Не налаштовано», worker failure
з error=not_configured при застарілому запуску. Застосунок узгоджує планування
з конфігурацією та autoSync; відключення скасовує також tagged one-shot роботу.
Явно збережені порожні поля більше не підміняються debug defaults.

Alisaphone API — exported Messenger bound service лише в src/personal і personal
manifest. Довіра перевіряється для **кожного Message** за sendingUid, allowlist
пакета com.alisaphone та SHA-256 поточних сертифікатів з personal BuildConfig.
Відсутні піни закривають доступ з caller_not_configured. Жодного debug bypass.
Команди й помилки описані в ALISAPHONE_API.md; доступу до мережі команди не потребують.
Записи й undo використовують TransactionRepository у Room withTransaction.
Для ownership резервуються випадкові від'ємні transaction ID; Room підтримує їх,
backup validation дозволяє ненульові signed transaction ID, позитивні ID рахунків
і категорій залишаються обов'язковими. Це не змінює таблиці: схема залишається v34.
Приватний durable undo-покажчик зберігає ID і fingerprint; змінену операцію сервіс
не видаляє. SharedPreferences та Room не мають спільного commit: при аварії undo
може стати недоступним, але перевірка ownership закриває доступ до інших операцій.

Тести написані, але Gradle/компіляція/пристрій у цій сесії не запускались за умовою
задачі. Статичні перевірки не є доказом проходження JVM або Android тестів.
