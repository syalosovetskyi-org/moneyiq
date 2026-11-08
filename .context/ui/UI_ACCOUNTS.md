# UI Contracts — Accounts Screen

## Accounts Screen

`AccountsScreen` (tab 0 — "Рахунки") is embedded inside the `HorizontalPager` in `MainScreen`. It receives action callbacks that are wired in `MainScreen`:

| Callback | Wired action |
|---|---|
| `onViewTx(acc)` | Sets `filterByAccountId = acc.id`, then `animateScrollToPage(txTabIndex)` — switches to Transactions tab **pre-filtered by that account** |
| `onAddIncome(acc)` | `onAddTransaction()` — opens AddTransaction screen |
| `onAddExpense(acc)` | `onAddTransaction()` — opens AddTransaction screen |
| `onAddTransfer(acc)` | `onAddTransaction()` — opens AddTransaction screen |

**"Операції" → account filter flow:**
`MainScreen` holds `filterByAccountId: Long?`. When `onViewTx(acc)` fires, it sets `filterByAccountId = acc.id` before navigating. `TransactionsListScreen` receives `initialAccountFilter = filterByAccountId` and clears it via `onInitialAccountFilterApplied`. Internally, `LaunchedEffect(initialAccountFilter)` sets `filterAccountIds = setOf(accId)`.

**Limitation**: Income / expense / transfer forms open without a pre-selected account.

**AccountActionSheet** (long-press on an account row) exposes six actions: Редагувати, Баланс, Операції, Поповнення, Списати, Переказ. Lives in `AccountPickerSheets.kt`. All callbacks are `() -> Unit` at the sheet level; `AccountsScreen` adapts them to `(AccountEntity) -> Unit` before forwarding to `MainScreen`.

**Rule:** When adding new action callbacks to `AccountsScreen`, always wire them in `MainScreen` — never leave them at the default `{}`. A silent no-op is indistinguishable from a crash to the user.

## Account Row Description

`AccountListItem` shows `account.description` directly below the name only when
`description.isNotBlank()`. It uses `MaterialTheme.typography.bodySmall` (12sp,
Regular) and `colorScheme.onSurfaceVariant`, with `maxLines = 1` and
`TextOverflow.Ellipsis`. For example: `VOO · 3,72 шт × $612,40`.

The existing balance remains below the name/description block; the icon and action
menu are preserved. Empty or whitespace-only descriptions add neither a text node
nor spacing, preserving the existing row height. The text column and row have no
fixed height, so they grow with the system font scale.

Regression coverage: `AccountsDescriptionTest` (Compose/Robolectric), at font
scales 1× and 2×, including removal of an existing description and blank values.

## Account Icon Currency Badge

Every account icon has a small **currency symbol badge** at bottom-right:

- **`AccountIconBox`** (`AccountsScreen.kt`): 20dp surface circle, `offset(2dp, 2dp)`. Text: 8sp Bold, color = `badgeColor`.
- **`AccountPickerSheet`** (`CalcDateSheet.kt`): 16dp circle, 7sp Bold.
- Symbol: `CURRENCIES_ALL.find { it.code == account.currency }?.symbol?.take(2)`. Fallback: first 2 chars of currency code.
- Star badge (`Alignment.BottomStart`) and currency badge (`Alignment.BottomEnd`) never overlap.

## Adaptive Icon Tint For Light-Colored Accounts

```kotlin
val isLightBg  = accentColor.luminance() > 0.5f
val iconTint   = if (isLightBg) Color(0xFF1C1B1F) else Color.White
val badgeColor = if (isLightBg) Color(0xFF1C1B1F) else accentColor
```

**Applied in:**
- `AccountIconBox` (`AccountsScreen.kt`) — icon tint + currency badge text
- `AccountActionSheet` card (`AccountPickerSheets.kt`) — all card content uses `onCard = if (isLightCard) dark else white`; icon box bg uses `onCard.copy(alpha=0.15f)`
- `AccountFormSheet` icon preview (`AccountSheets.kt`) — `iconTint` from `accentColor.luminance()`
- `IncomeBudgetInputSheet` account picker list items (`BudgetSheets.kt`) — `accIconTint` per item

**Rule:** Never hardcode `tint = Color.White` on an icon whose background is `account.colorHex`. Threshold `> 0.5f` → use `Color(0xFF1C1B1F)`.

## AccountFormSheet — Credit Limit Field

`AccountFormSheet` (`AccountSheets.kt`) contains a "Кредитний ліміт" row in the "Баланс" section. Tapping it opens `AmountCalculatorSheet` and stores the result in `creditLimit: Double` state.

**`onSave` signature (9 parameters):**
```kotlin
onSave: (name: String, type: AccountType, balance: Double, color: String,
         currency: String, description: String, includeInTotal: Boolean,
         icon: String, creditLimit: Double) -> Unit
```

**Data flow:**
- Edit path (`AccountsScreen.kt`): `acc.copy(creditLimit = creditLimit)` → `viewModel.update()`
- Create path (`MainScreen.kt`): `accountsViewModel.add(..., creditLimit = creditLimit)`

**DB:** `accounts.creditLimit REAL NOT NULL DEFAULT 0.0`, migration 26→27.

**Display:** `"0 $sym"` when zero; `"${value} $sym"` otherwise (stripped trailing zeros).

**Rule:** Do not pass a hardcoded `0` for `creditLimit`. Always destructure and forward so existing limits survive re-saves.

## CurrencyPickerSheet — `title` Parameter

`CurrencyPickerSheet` (`AccountPickerSheets.kt`) has `title: String = "Валюта рахунку"`. Override when reusing in other contexts (e.g., `"Валюта транзакції"` in `AddTransactionScreen`).
