# DB Schema

Room database: `AppDatabase`

Current version: `34`

Entities:

- `accounts`
- `categories`
- `transactions`
- `tx_overrides`

## Accounts

`AccountEntity`

- `id: Long` primary key, auto-generated
- `name: String`
- `type: AccountType` (`CASH`, `CARD`, `SAVING`, `INVESTMENT`, `DEBT`, `OTHER`)
- `balance: Double`
- `currency: String`, default `UAH`
- `colorHex: String`
- `icon: String`
- `includeInTotal: Boolean`
- `isDefault: Boolean`
- `sortOrder: Int`
- `description: String`
- `createdAt: Long`
- `creditLimit: Double`, default `0.0` (added migration 26→27)

## Categories

`CategoryEntity`

- `id: Long` primary key, auto-generated
- `name: String`
- `type: TransactionType`
- `colorHex: String`
- `icon: String`
- `budgetAmount: Double`
- `budgetPeriod: String`
- `isDefault: Boolean`
- `sortOrder: Int`
- `archived: Boolean`
- `parentId: Long?`
- `currencyCode: String`, default `"UAH"` (added migration 20→21)

`parentId == null` means a root/broad category.

## Transactions

`TransactionEntity`

- `id: Long` primary key, auto-generated
- `type: TransactionType`
- `amount: Double`
- `accountId: Long`
- `toAccountId: Long?`
- `toAmount: Double?` (added migration 33→34) — сколько зачислено на счёт-получатель; `null` = столько же, сколько списано (счета одной валюты). Хранится, а не пересчитывается: курс меняется, и откат операции по свежему курсу оставил бы расхождение на счетах
- `categoryId: Long?`
- `note: String`
- `date: Long`
- `createdAt: Long`
- `repeatMode: String`, default `"NEVER"` (added migration 27→28)
- `reminderMode: String`, default `"NEVER"` (added migration 27→28)
- `nextRepeatDate: Long?` (added migration 27→28) — timestamp следующего автовхождения; `null` если repeat не используется или последнее вхождение уже создано

Foreign keys:

- `accountId` -> `accounts.id`, cascade delete
- `toAccountId` -> `accounts.id`, set null on delete
- `categoryId` -> `categories.id`, set null on delete

Indices:

- `accountId`
- `toAccountId`
- `categoryId`
- `date`

## Tx Overrides

`TxOverrideEntity` — очередь правок операций, которые надо отдать на mono-flow.
Синк тянет операции с сервера и перезаписывает их по id (REPLACE), поэтому правка
операции, пришедшей с сервера, живёт до следующего синка, если не доехала обратно.

- `txId: Long` primary key — id операции (тот же, что пришёл с сервера)
- `note: String` — новое имя операции
- `categoryName: String?` — ИМЯ категории, не id: id категории на сервере производится
  от имени, имя переживает пересборку выдачи
- `txType: String` — `EXPENSE` / `INCOME` / `TRANSFER`
- `updatedAt: Long`
- `synced: Boolean`, default `false` — снимается только после подтверждения сервером
- `attempts: Int`, default `0` (added migration 31→32) — сколько раз сервер ОТКАЗАЛСЯ принять
  правку; сетевые сбои не считаются. После `TxOverrideRepository.MAX_ATTEMPTS` правка
  перестаёт блокировать синк, но остаётся в таблице
- `lastError: String?` (added migration 31→32) — причина последнего отказа

Строки с id меньше 10^12 сюда не попадают: такая операция заведена локально, сервер
её не знает (см. `TxOverrideRepository.SERVER_ID_THRESHOLD`).

## Migrations

- `1 -> 2`: adds `accounts.isDefault`
- `2 -> 3`: adds `accounts.description`
- `3 -> 4`: adds `categories.archived`
- `4 -> 5`: adds `categories.parentId`
- `5 -> 6`: data migration — updates `icon`/`colorHex` for Продукти (`grocery`/`#03A9F4`), Дозвілля (`ticket`/`#E91E63`), Здоров'я (`volunteer`/`#4CAF50`)
- `6 -> 7`: data migration — updates `icon`/`colorHex` for Таксі (`taxi`/`#FDD835`), АЗС (`gas_station`/`#FF8F00`)
- `7 -> 8`: data migration — updates `icon` for Дозвілля/Розваги/Кіно → `movie`; Gaming → `gaming`; Telegram → `telegram`; Dating → `dating`
- `8 -> 9`: data migration — Дозвілля: icon → `theater`, colorHex → `#7B1FA2`; Транспорт: colorHex → `#00897B`
- `9 -> 10`: data migration — Зв'язок: icon → `phone`, colorHex → `#3F51B5`; Інтернет: icon → `wifi`, colorHex → `#00BCD4`
- `10 -> 11`: data migration — Комуналка/Комунальні/Комунальне: icon → `home`, colorHex → `#546E7A`
- `11 -> 12`: data migration — Фінанси: icon → `money`, colorHex → `#F9A825` (LIKE `%фінанс%`)
- `12 -> 13`: data migration — DELETE EXPENSE categories matching `%фінанс%` or `%финанс%` (finance ≠ expense)
- `13 -> 14`: data migration — root category color palette refresh: Продукти `#4AAFE8`, Ресторація `#4659BE`, Дозвілля `#F73579`, Транспорт `#FFA834`, Здоров'я `#48B456`, Подарунки `#F34B4D`, Сім'я `#7A48F2`, Покупки `#7B5947`
- `14 -> 15`: data migration — single authoritative subcategory icon fix: `delivery`/`#FF6F00` for food delivery; `coffee`/`#795548` for кафе; `restaurant`/`#E53935` for ресторани (parentId IS NOT NULL guard on all; TRIM+LIKE matching); fixes `movie`→`#9C27B0` and `coffee`→`#795548` iconColorMap bugs
- `15 -> 16`: data migration — safety net: sets `money`/`#F9A825` for any remaining "Фінанс*" categories by name
- `16 -> 17`: data migration — re-applies Ресторація subcategory icons using exact name matching (bypasses SQLite Cyrillic `LOWER()` bug): `delivery` for food delivery apps, `coffee` for Кафе, `restaurant` for Ресторани
- `17 -> 18`: data migration — sets `bus` icon for root "Транспорт" (`parentId IS NULL AND name = 'Транспорт'`)
- `18 -> 19`: data migration — fixes categories still stuck on `category` placeholder: `money`/`#F9A825` for фінанс*, `celebration`/`#FF6D00` for розваг*, `theater`/`#F73579` for дозвілл*
- `19 -> 20`: data migration — restores `bus` for Транспорт; assigns distinct icons to subcategories: `pharmacy`/Аптека, `doctor`/Лікар, `parking`/Паркінг, `gas_station`/Пальне, `key`/Оренда, `laptop`/Фриланс, `sports`/Спорт
- `20 -> 21`: **structural** — adds `categories.currencyCode TEXT NOT NULL DEFAULT 'UAH'`
- `21 -> 22`: data migration — fixes stale icons from old `suggestCategoryStyle` for existing rows: `delivery` (кур'єр/доставка), `clothes` (одяг/взуття), `school` (освіта/навчання), `devices` (техніка/гаджети), `doctor` (стоматолог), `sports` (спортивн*), `parking` (паркування), `percent` (проценти/відсоток/податки), `gavel` (штраф/пеня)
- `22 -> 23`: data migration — fixes root utilities icons: `home`/`#546E7A` (комунал*), `phone`/`#3F51B5` (зв'язок), `wifi`/`#00BCD4` (інтернет)
- `23 -> 24`: data migration — fixes Здоров'я root stuck on old `health`/`doctor` icon → `volunteer`/`#48B456`; fixes Спорт stuck on health icon → `sports`; fixes any remaining root-level `health` icon → `volunteer`
- `24 -> 25`: data migration — unconditional fix for Дозвілля (`theater`/`#F73579`) and Розваги (`celebration`/`#FF6D00`); no `icon = 'category'` guard because imported data can overwrite prior migrations via REPLACE strategy
- `25 -> 26`: data migration — inserts default income categories (`Зарплата`/`work`/`#4CAF50`, `Фриланс`/`laptop`/`#26A69A`, `Інше`/`category`/`#78909C`) using conditional INSERT … WHERE NOT EXISTS, so existing categories are never duplicated
- `26 -> 27`: **structural** — adds `accounts.creditLimit REAL NOT NULL DEFAULT 0.0`
- `27 -> 28`: **structural** — adds `transactions.repeatMode TEXT NOT NULL DEFAULT 'NEVER'`, `transactions.reminderMode TEXT NOT NULL DEFAULT 'NEVER'`, `transactions.nextRepeatDate INTEGER` (nullable)
- `28 -> 29`: data migration — fixes root-level imported categories stuck on `family` icon: `home`/`#546E7A` (LIKE `%комунал%`), `phone`/`#3F51B5` (LIKE `%зв%язок%`, apostrophe-agnostic), `wifi`/`#00BCD4` (= `інтернет`). Unconditional — no icon guard, since `family` is a valid key and prior name-based migrations were bypassed when these categories were imported after migration.
- `29 -> 30`: data migration — one-time `sortOrder` fix for 9 root expense categories: продукти→1, ресторація→2, дозвілля→3, транспорт→4, здоров'я→5, подарунки→6, сім'я→7, покупки→8, робота→9. Uses LIKE for apostrophe names. After this migration `sortOrder` is user-controlled and never reset on startup.

- `30 -> 31`: adds table `tx_overrides` (очередь правок операций для mono-flow)
- `31 -> 32`: adds `tx_overrides.attempts` and `tx_overrides.lastError`
- `32 -> 33`: **structural** — adds `tx_overrides.toAccountId INTEGER` (счёт-получатель для правки «расход → перемещение»)
- `33 -> 34`: **structural** — adds `transactions.toAmount REAL` (nullable). NULL читается как «столько же, сколько списано», то есть поведение до миграции

Any schema change must add a migration and update this file.

## DAO write safety (2026-11-04)

AccountDao and CategoryDao use `@Upsert` for single and bulk insert-or-update.
This preserves transaction foreign keys during sync and saves. The schema remains
v34; no tables or columns changed. SQLite REPLACE would delete parent rows and
trigger CASCADE/SET NULL before reinserting them.

## Personal API transaction identity (2026-11-08, #2673)

Schema remains v34. Personal Alisaphone API reserves random **negative nonzero**
transaction IDs; ordinary auto-generated and server IDs remain positive. Room's
INTEGER primary key supports this without migration. JSON validation accepts signed
nonzero transaction IDs, while account/category IDs must remain positive. API undo
ownership lives in private SharedPreferences (ID + original fingerprint), not in the
financial schema or portable JSON backup. A negative ID alone never authorizes undo.
