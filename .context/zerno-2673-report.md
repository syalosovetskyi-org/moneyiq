# Zerno / serg/tasks#2673 — частичный результат, 2026-11-04

**Критерий готовности НЕ достигнут. Новых коммитов нет. Kotlin не компилировался,
Android/JVM тестов выполнено 0, APK не собран. Полное покрытие не достигнуто.**
Изменения сохранены в рабочем дереве `codex/moneyiq-review-2673`.

## Коммиты

| SHA | Содержание |
|---|---|
| `6ab327b` | Предыдущий заход: 8 исправлений учёта и 13 регрессионных тестов; уже был в HEAD |
| — | Фаза A: изменения подготовлены, коммит не создан |
| — | Фаза B: тестовая база расширена, полное покрытие не закончено, коммит не создан |
| — | Фаза C: исправления и регрессии подготовлены частично, коммит не создан |

`git add` и `git commit` не могут создать
`/root/work/moneyiq-2673/.git/worktrees/moneyiq-2673-moneyiq-review-2673/index.lock`:
`Read-only file system`. Запрос расширенного права записи отклонён. Переносить
Git metadata или обходить ограничение не пытался. Ни push, ни публикация не выполнялись.

## Проверки

| Команда | Итог |
|---|---|
| `cd onemoney && bash ./gradlew --offline :app:testDebugUnitTest :app:assembleDebug` | FAILED до старта Gradle: `/root/.gradle/...zip.lck` — read-only |
| Та же команда с разрешением записи вне sandbox | Не запускалась: запрос отклонён |
| `GRADLE_USER_HOME="$PWD/.gradle-offline" GRADLE_RO_DEP_CACHE=/root/.gradle/caches/modules-2 bash ./gradlew --offline :app:testDebugUnitTest :app:assembleDebug --console=plain` из `onemoney/` | Gradle 8.11.1 стартовал, затем FAILED: `Could not determine a usable wildcard IP for this machine` |
| `python3 verify_zerno_2673.py` | PASS: XML ресурсов; launcher/имя/APK; SQL всех 33 миграций с сохранением fixture; контраст 8 денежных токенов |
| `git diff --check` | PASS |

**JUnit:** 0 выполнено; число упавших неизвестно — test discovery не начинался.
**BUILD SUCCESSFUL отсутствует. Результат сборки — FAILED до компиляции.**
Полный вывод workspace-запуска: `zerno-2673-gradle.log`.
Независимая Python-проверка не проверяет компиляцию Kotlin, Room schema validation,
Compose rendering или работу Android provider’ов и не заменяет JUnit.

`work-testing` найден в существующем офлайн-кеше; зависимость `2.10.0` добавлена.
`compose-ui-test-junit4` подключён в testImplementation; test-manifest уже был в
 debugImplementation, Android resources для unit tests уже были включены.
Не заявляю отсутствия конкретной библиотеки: Gradle не достиг dependency resolution.
SDK Platform 36 и Robolectric android-all также не проверены успешной сборкой.
Сеть не использовалась. Макет не открывался; использованы значения из брифа.

## Покрытие

Полная таблица: [coverage-matrix-2673.md](coverage-matrix-2673.md).

| Категория строк | Количество |
|---|---:|
| Добавлен тест / регрессия | 42 |
| Есть существующий или перенесённый тест | 23 |
| Частичное покрытие | 19 |
| Реальный пробел — отдельного теста нет | 23 |
| Обоснованное исключение — файл констант без логики | 1 |
| Всего | 108 |
| Полностью закрыто и подтверждено прогоном | **0** |

В исходниках 401 аннотация `@Test` (было 270). Это **131 добавленный тестовый метод**,
включая перенос DAO/schema-тестов в Robolectric. Один метод миграций параметризован
по 33 версиям. Это счёт исходников, не число выполненных тестов и не coverage percentage.

Добавлены: рендер 9 экранов с пустым/обновлённым состоянием; drawer callbacks;
подтверждение удаления бюджета; сохранение состояния калькулятора и имён форм;
settings VM с StandardTestDispatcher/setMain; DAO/schema под Robolectric;
миграции v1…v33 → v34; все 5 worker-классов через TestListenableWorkerBuilder;
ошибки/rollback восстановления и повторов; файлы, валидация бэкапа, валютные
группы, форматирование и контраст.

**Остаток фазы B:** полноценный CRUD через UI, действия всех форм и picker’ов,
полная навигация оболочки/месяцев, main shell, реальные списки операций в UI-тесте,
успешный SAF export/restore/rename с provider’ом, CSV chooser, успешный HTTP-синк,
полное покрытие SettingsRepository/CurrencyRatesRepository, widgets. Перенесённые
DAO-тесты и fixture миграций также требуют фактического прогона. Причины и текущие
файлы указаны для каждой строки матрицы. Эти пробелы не маскируются VM-тестами.

## Баги и статус реализации

Пути относительно `onemoney/app/src/main/java/org/pixelrush/moneyiq/`.
«Внесено» означает изменение исходников; ни одно Kotlin-исправление не прошло сборку.

| № | Статус | Файл:строка и изменение | Ограничение |
|---|---|---|---|
| 1 | Внесено, частично проверено независимо | `util/AtomicBackupFile.kt:9`, `ui/data/DataViewModel.kt` createLocalBackup; `workers/DriveBackupWorker.kt` writeBackupDocument: temp → close/fsync → rename | SAF atomicity зависит от provider; прямой SAF export в уже выданный URI не может гарантировать атомарный rename |
| 2 | Внесено | `util/BackupSerializer.kt:25`, `data/repository/BackupStore.kt:17`: сначала версия/ID/FK/finite, затем транзакция | В тесте инъекция SQL failure и проверка rollback; JVM не запускался |
| 3 | **Частично, остаётся открытым** | `util/CurrencyTotals.kt:9`, `ui/transactions/TransactionsListScreen.kt`; `util/CurrencyTotals.kt:21`, `ui/accounts/AccountsScreen.kt`: группы по валюте | Reports/overview/budget/category aggregates и opening-balance ещё могут смешивать валюты |
| 4 | Внесено | `ui/components/calculator/CalcState.kt:12`, `:29`; `ui/transactions/TransactionViewModel.kt:102`: finite checks, безопасный overflow, валидация save | Overflow отображается как 0 и логируется; отдельного текста ошибки калькулятора пока нет |
| 5 | Внесено | `ui/budget/BudgetViewModel.kt:113`, `BudgetCatRow.budgetForPeriod`; `ui/budget/BudgetScreen.kt`: allowance по дням/7 | Правило описано в ADR-076; смешанные валюты бюджета остаются открытыми |
| 6 | Внесено | `workers/RepeatTransactionWorker.kt:165`: повторная проверка marker и все изменения в одной Room transaction | Тест stale claim и rollback при SQL failure не запускался |
| 7 | Внесено | `ui/transactions/TransactionViewModel.kt:120`: copy исходной операции | Сохраняются repeat/reminder/nextRepeatDate/createdAt/toAmount |
| 8 | **Частично** | `util/MoneyFormat.kt:11`, `ui/settings/data/SettingsData.kt:49`: uk-UA, символы, Unicode minus, корректный перенос округления | Все call sites и пользовательский формат ещё не унифицированы; остаются ручные ₴ |
| 9 | Внесено | `ui/budget/BudgetScreen.kt:174`: ConfirmationDialog перед clearAllBudgets | Тексты добавлены uk/en/ru; отмена не тестировалась отдельно |
| 10 | Внесено для drawer | `ui/main/MainScreen.kt:361`: оставлены Settings/Data, удалены no-op пункты и поиск | Неиспользуемый legacy MoreScreen удалён: ни NavGraph, ни MainScreen на него не ссылались; полный navigation-test отсутствует |
| 11 | **Частично** | `ui/components/calculator/CalcState.kt:82`; AccountSheets, CategoryFormSheets, QuickExpenseSheet, TransactionDetailSheet, BudgetSheets, IconColorPickerScreen | TransferQuickSheet, выбранные родительские sheet/entity и некоторые UI-флаги ещё требуют saveable IDs/Savers |
| 12 | Внесено для денежных токенов | `ui/theme/Theme.kt:17`, `OneMoneyTokens.kt`; UI использует существующий CompositionLocal | Python: все 8 контрастов ≥4.5:1 на заданных surface; полный визуальный аудит отсутствует |

## Фаза A

Подготовлены Zerno app_name, drawer (иконка/название/подпись), about/biometric/notification
названия, векторный foreground/background/full, adaptive + monochrome; удалены старые
launcher PNG и 7 отслеживаемых APK; `*.apk` добавлен в .gitignore. Навигационные
клоны заменены Material Outlined. Задан seed по умолчанию с сохранением сохранённого
пользовательского акцента; точные supplied light/dark M3 поля и денежные токены.
applicationId, namespace и каталог `org.pixelrush.moneyiq` не менялись. Внутренние
имена theme/package/DB и JSON app identifier остаются совместимыми.

## Double → целые minor units / BigDecimal: только план

Оценка: **8–14 рабочих дней** одного Android-разработчика, включая миграцию,
интеграционные тесты и проверку восстановления. Это ориентир после статического
осмотра, не подтверждённая оценка по собранному проекту.

1. Инвентаризация: balances, creditLimit, amount/toAmount, category budgets, income
   budget в DataStore, агрегаты DAO, ставки конвертации, калькулятор, JSON/CSV/widgets.
   Определить scale для каждой поддерживаемой валюты: не все валюты имеют 2 знака;
   для JPY/KWD/криптовалют фиксированные «копейки» недостаточны.
2. Ввести `Money(minor: Long, currency)` с арифметикой только одной валюты и
   overflow-check. BigDecimal использовать для ввода, курсов и промежуточной
   конвертации; согласовать rounding, округлять при записи, сохранять toAmount.
3. Room v35: новые INTEGER-колонки/пересоздание таблиц, перенос `Double` через
   явно согласованный scale/rounding; до переноса проверить nonfinite и границы.
   Добавить MIGRATION_34_35, ALL_MIGRATIONS, schema docs и upgrade tests с историей.
   Не делать destructive fallback и не менять существующие суммы молча.
4. Перевести Repository/DAO/Worker вместе, затем ViewModel/формы/агрегаты/widgets.
   Разделять валюты в типах: запретить случайный SUM разных валют.
5. Backup v2 с backward reader v1 и проверкой переполнения; CSV хранит decimal
   представление, UI форматирует minor units без Double. Отдельно перенос DataStore.
6. Регрессии: 0.1+0.2, отрицательный ноль, large amounts, overflow, валюты разных
   scale, FX round trip/удаление исторического перевода, rollback, repeat retry,
   Room migration и JSON v1/v2. Прогнать upgrade на копии данных и emulator smoke.

Миграция денег в этом заходе **не выполнялась**; Room остаётся v34.
