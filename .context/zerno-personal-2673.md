# Zerno personal — #2673

Дата: 2026-11-08. Linux Bash. Изменения оставлены в рабочем дереве, коммитов нет.
**Gradle, Kotlin-компиляция, JVM/Robolectric-тесты и эмулятор не запускались**:
это ограничение задачи. Утверждений о red/green до/после фикса нет.

## Исправления и корень багов 2–6

Основной корень — разные денежные единицы на границе данных и UI:
`AccountRepository` переводил баланс в UAH, но неизвестную валюту умножал на 0;
DAO-суммы категорий/операций оставались в валюте счёта, а экран подписывал их ₴.
Для RUB без кешированного курса это давало одновременно Balance=0 и Expenses=200 ₴.
В Operations формула `currentBalance - income + expense` смешивала эти единицы;
сама алгебра знака для текущего периода была правильной. Дополнительно текущий
баланс ошибочно использовался как Closing для любого исторического периода.
В Budget был отдельный дефект: условие прогноза не требовало дохода/бюджета дохода.

Здесь базовая валюта межсчётных отчётов — UAH, как в существующих заголовках и
AccountRepository. Конвертация использует текущий кеш НБУ, не исторический FX.
Исходные суммы/валюты операций не переписываются.

Все пути Java/Kotlin ниже относительно `onemoney/app/src/main/java/org/pixelrush/moneyiq/`:

| Баг / изменение | Файл:строка | Поведение |
|---|---|---|
| 1. Начальный счёт | `MoneyIQApp.kt:85` | `initialAccount`: ресурсное имя uk Гаманець / en Wallet / ru Кошелёк, UAH, isDefault. Вызов остаётся под count==0; существующие счета не меняются |
| 2. Общий баланс | `data/repository/AccountRepository.kt:21`, `util/BaseValuation.kt:15` | Конвертация по кешу с BaseValuation(amount, missingCurrencies), отрицательные остатки сохраняют знак |
| 2. Нет курса | `ui/main/CurrencyStatusViewModel.kt:14`, `ui/main/MainScreen.kt:246` | Общая плашка «Partial total: no rate for RUB…» на вкладках; неизвестные суммы исключены из частичного UAH-итога, не выдаются за успешный ноль |
| 3. Категории/Expenses | `data/repository/TransactionRepository.kt:68` | Конвертация до агрегации getCategorySpending/getIncomeSum/getExpenseSum; её получают Categories, Budget, Main, Reports. Исходный getTransactionsByPeriod остаётся для редактирования |
| 4. Opening/Closing | `util/BaseValuation.kt:32`, `ui/transactions/TransactionsListViewModel.kt:60` | Балансы на границах периода восстанавливаются от текущих остатков, включая поздние операции, excluded accounts и transfer.toAmount. После первого расхода 200 UAH: Opening=0, Closing=−200 |
| 5. Overview | `ui/overview/OverviewViewModel.kt:95` | Графики/статистика читают базовую проекцию операций, Balance — общий конвертированный баланс; при первом расходе 200 UAH он −200 |
| 6. Бессмысленный прогноз | `ui/budget/BudgetScreen.kt:601`, `util/BaseValuation.kt:48` | hasForecast требует дохода или incomeBudget, положительных расходов и неполного прошедшего периода. Реальные отрицательные savings остаются видимыми без forecast |
| Дополнительные сырые суммы | `ui/transactions/CategoryPickerSheet.kt`, `ui/components/calculator/CalcDateSheet.kt`, `ui/widget/BalanceWidget.kt:50` | Итоги выбора счетов и виджета показываются раздельно по валютам; данные для категорий в Operations берутся из baseTransactions |

Проверены потребители общего баланса: Main, Accounts, Overview, Budget, Operations,
пикеры счетов/категорий. Прямой `AccountDao.getTotalBalance()` остался для совместимости,
production UI его не вызывает. На финансах Accounts и итогах Operations уже были
раздельные валютные суммы — эта честная семантика сохранена.

## Mono-flow только personal

- `onemoney/app/build.gradle.kts:101`: MONOFLOW_ENABLED true/false по flavor.
  DEBUG_MONOFLOW_URL/TOKEN отсутствуют в Play; заполняются variant API только у
  personalDebug, personalRelease хранит пустые строки. MonoFlowDefaults находится
  отдельно в src/personal и src/play. Пины Alisaphone тоже только personal.
- `workers/MonoFlowPolicy.kt:9`: единое условие flavor + непустые URL/токен.
- `workers/MonoFlowSyncWorker.kt:53`, `workers/TxOverridePushWorker.kt:33`:
  застаревший запуск без настройки возвращает failure с error=not_configured;
  Play возвращает error=disabled до доступа к Hilt/сети. Skipped push больше не success.
- `MoneyIQApp.kt`: отслеживание конфигурации/autoSync на старте и при изменении,
  отмена периодической и one-shot работы при отключении; cancellation охватывает
  class tags старых запросов. Методы schedule проверяют конфигурацию сами.
- `ui/data/DataScreen.kt`: MonoFlow-секция скрыта в Play; `DataWidgets.kt:73`:
  явное Not configured / Не налаштовано / Не настроено.
- `SettingsRepository.kt`: сохранённая пустая строка отключает debug defaults,
  поэтому Disconnect действительно отключает интеграцию после перезапуска.
- `TxOverrideRepository.kt`: Play не записывает override и не отправляет очередь.

## Локальный Alisaphone API

Полный контракт, JSON-форматы, ошибки и Kotlin-клиент: `.context/ALISAPHONE_API.md`.
ADR-077 добавлен в `.context/ADR_LOG.md`.

Файлы относительно `onemoney/app/src/personal/java/org/syalosovetskyi/onemoney/alisaphone/`:

| Файл:строка | Реализация |
|---|---|
| `AlisaphoneService.kt:13` | Exported Messenger bound Service; identity из Message.sendingUid; reply Bundle ok/error/result; ошибки авторизации/неожиданные сбои и потерянные ответы логируются |
| `CallerVerifier.kt:9` | UID→пакеты, allowlist com.alisaphone, SHA-256 текущих подписей, comma-separated pins; пустая конфигурация закрывает все команды |
| `ApiRequest.kt:36` | Известные команды и ключи, строгие типы/диапазоны, ISO date/month; неизвестные параметры не игнорируются |
| `AlisaphoneCommands.kt:61` | add_transaction через TransactionRepository внутри Room transaction; default account как в quick entry; uk/ru/en alias search и ближайшие имена при ошибке |
| `AlisaphoneCommands.kt:103` | undo_last только последней API-записи по ID+fingerprint, с возвратом баланса, без удаления ручных или изменённых записей |

Тот же обработчик реализует balance, recent, month_summary и open. Balance/summary
возвращают complete и missing_rates. Суммы add сохраняются в валюте счёта после
офлайн-конвертации; без нужного курса запись отклоняется.

`src/personal/AndroidManifest.xml` экспортирует сервис и объявляет visibility
com.alisaphone. В Play записи сервиса нет. `MainActivity.kt:75`, NavGraph/MainScreen
принимают zerno.screen и обрабатывают холодные/повторные warm-запуски.

API использует случайные отрицательные transaction ID, чтобы не пересечься с
обычным автоинкрементом и серверными операциями. `util/BackupSerializer.kt:27`
принимает такие ID для транзакций; нулевые ID, дубликаты и некорректные FK по-прежнему
отклоняются. Схема Room **остаётся v34**, миграция не нужна; DB_SCHEMA обновлён.

## Написанные тесты

- `src/test/.../InitialAccountTest.kt`: баг 1, локали uk/en/ru и UAH.
- `src/test/.../data/repository/PersonalCurrencyRegressionTest.kt`: отдельные
  регрессии 2–6, отсутствие курса, сохранение исходной валюты, исторические границы,
  перевод на исключённый из итога счёт. Проверяется production repository/helper.
- Существующие OverviewViewModelTest и TransactionsListViewModelTest обновлены
  под базовую проекцию/границы периода.
- `src/testPersonal/.../MonoFlowFlavorTest.kt`, `src/testPlay/.../MonoFlowFlavorTest.kt`:
  flavor/config gates, отсутствие секретных полей/сервиса в Play, отсутствие очереди.
- `src/test/.../workers/WorkerBehaviorTest.kt`: failure при пропуске, flavor-запрет,
  отсутствие work без конфигурации и планирование только personal при настройке.
- HTTP-тест TxOverrideRepositoryTest перенесён из общего test в testPersonal,
  поскольку в Play отправка запрещена; содержимое теста сохранено.
- `src/testPersonal/.../alisaphone/CallerVerifierTest.kt`: доверенный caller,
  чужой пакет/сертификат, отсутствие/некорректность pins, rotation/multisigner.
- AndroidCallerVerifierTest: реальный SHA-256 Signature.toByteArray через
  PackageManager-путь API 27. ApiRequestTest: amount/limit/date/month, неизвестные
  команды/поля, Unicode/алиасы категорий.
- AlisaphoneCommandsTest: Room in-memory add+undo, сохранность ручных операций,
  durable ownership после recreation, защита изменённой записи, конвертация,
  неизвестная/неоднозначная категория, SQL fault injection с rollback,
  balance/recent/summary и валидируемый backup round-trip API-записи.

Эти тесты **не запускались**. Не заявляется, что они компилируются или что фактический
pre-fix red / post-fix green получен. Статическая проверка пройдена только для
`git diff --check` и XML: два flavor manifest + три локализованных strings.xml
парсятся, имена ресурсов не дублируются. Это не Android manifest merge/AAPT.

Команды для проверки пользователем:

```bash
cd onemoney
./gradlew :app:testPersonalDebugUnitTest :app:testPlayDebugUnitTest
./gradlew :app:assemblePersonalDebug :app:assemblePlayDebug
```

На устройстве: свежая personalDebug en → Categories → Продукти → 200; затем старый
RUB-счёт с доступным/недоступным курсом; вкладки Operations/Overview/Budget; Data до
и после конфигурации; Play Data без MonoFlow. Для IPC — установленный com.alisaphone
с закреплённым сертификатом, затем другой сертификат и отсутствующие pins; проверить
в том числе open при уже открытом приложении и действии Android BAL-ограничений.

## План коммитов (не выполнен)

В общих файлах `build.gradle.kts`, `MainScreen.kt`, ресурсах и `MoneyIQApp.kt`
изменения разных частей нужно разделять по hunks, чтобы коммиты оставались сборными.

1. `fix: локалізувати початковий рахунок і узгодити валютні підсумки (#2673)`
   - MoneyIQApp seed; default_account_name/missing_currency_rates в strings;
     util/BaseValuation; AccountRepository/CurrencyRatesRepository/TransactionRepository;
     TransactionDao.observeAllTransactions; CurrencyStatusViewModel;
     MainScreen missing-rate banner; OverviewViewModel; TransactionsListViewModel/Screen;
     BudgetScreen; CategoryPickerSheet; CalcDateSheet; BalanceWidget.
   - InitialAccountTest, PersonalCurrencyRegressionTest, обновлённые Overview/Operations тесты.
2. `fix(personal): ізолювати mono-flow та явно показувати відсутню конфігурацію (#2673)`
   - build.gradle flavor/debug MonoFlow fields; personal/play MonoFlowDefaults;
     SettingsRepository; MonoFlowPolicy; оба sync/push worker; TxOverrideRepository;
     MoneyIQApp scheduling; DataScreen/ViewModel/Widgets и Not configured resources.
   - WorkerBehaviorTest, оба MonoFlowFlavorTest, перенос TxOverrideRepositoryTest в personal.
3. `feat(personal): додати локальний Messenger API для Alisaphone (#2673)`
   - personal/alisaphone/*, personal manifest, build.gradle ALISAPHONE_CERT_SHA256;
     MainActivity/NavGraph/MainScreen deep-link hunks; BackupSerializer signed transaction ID.
   - testPersonal/alisaphone/*.
4. `docs: описати API Alisaphone та перевірки personal (#2673)`
   - .context/ALISAPHONE_API.md, ADR_LOG.md (ADR-077), DB_SCHEMA.md,
     bug-log.md, этот отчёт.

## Что не сделано / ограничения

- Не выполнены сборки, JVM/Robolectric и device-проверки — Gradle/сеть недоступны
  по условию, пользователь запускает их самостоятельно. Итоговая приёмка до них открыта.
- Не реализован клиент Alisaphone: он поручен другой сессии. Реальный сертификат
  не подставлялся/не коммитился; до настройки pins API явно закрыт.
- Кеш НБУ не гарантирует наличие RUB/криптовалют. Отсутствие курса явно показывается,
  фиктивные курсы не добавлялись. Исторические курсы не хранятся; используются текущие
  кешированные курсы, как в существующем AccountRepository.
- Собственные валюты лимитов категорий не менялись: эта работа приводит **операции
  и балансы** к UAH. Пересчёт лимитов category.currencyCode вне UAH остаётся отдельным
  ограничением ADR-076; к сценарию нулевого бюджета из задачи он не применяется.
- SharedPreferences и Room не имеют общего commit. При аварии в узком окне durable
  undo может стать недоступным; ID+fingerprint не дают откатить другую операцию.
  Ownership не переносится в JSON backup на другое устройство. Сами финансовые
  данные и баланс защищены Room transaction.
- У add_transaction нет idempotency key: после потерянного ответа клиент проверяет
  recent перед повтором. open сообщает отправку Intent; реальный вывод Activity
  зависит от системных разрешений запуска из фона и требует проверки на телефоне.
