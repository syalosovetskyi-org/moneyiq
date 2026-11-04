# Zerno #2673 — второй заход, 2026-11-04

Изменения оставлены в рабочем дереве. Коммитов не создавал, Git использовал только для чтения, Gradle не запускал. Компиляция и результат тестов этого раунда **не подтверждены**.

## Шесть падений пользовательского прогона

- `onemoney/app/src/test/java/org/pixelrush/moneyiq/data/db/ParentUpsertTest.kt:20`: Robolectric использует обычный `android.app.Application`; тест Room больше не запускает Hilt Application и фоновые задачи. Это исправляет причину двух падений.
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/MoneyIQApp.kt:34`: scheduleOnce обёрнут в try/catch с warning и отправкой пойманного исключения через flavor CrashReporter. Ошибка планирования не должна прерывать запуск приложения.
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/ui/categories/CategoryFormSheets.kt:556`: эффект перенесён внутрь реально создаваемого lazy header. Фокус запрашивается после onGloballyPositioned/isAttached; возможное отсоединение между layout и эффектом обработано с warning. Старый эффект внешнего sheet мог срабатывать до создания поля внутри LazyColumn. Тест восстановления сохранён; дополнительно добавлена проверка validation/submit формы.
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/ui/categories/CategorySheets.kt:739`: явный `Collator` uk со strength SECONDARY, включая tie-break корневых категорий. Пользовательский sortOrder сохраняется; порядок детей не зависит от регистра или системной locale. Ожидания обоих тестов не менялись.
- `onemoney/app/src/test/java/org/pixelrush/moneyiq/ui/ScreenRenderTest.kt:70`: Overview использует актуальный state.categories, но категории находятся после balance/toggle/chart/stats в LazyColumn. В исходном тесте строка ещё не была скомпозирована вне viewport. Тест теперь прокручивает настоящий список до строки и проверяет assertIsDisplayed; экран и ожидаемый текст не менялись.

Это исправления причин по исходникам, а не заявление, что все шесть тестов теперь проходят.

## Crashlytics и flavors

- `onemoney/app/build.gradle.kts:99`: flavors personal/play. До изменений applicationId был `org.syalosovetskyi.onemoney`; personal его сохраняет, play использует тот же id. Эти APK заменяют друг друга на устройстве, параллельная установка с разными id не предусмотрена.
- `onemoney/gradle/libs.versions.toml:21`, `onemoney/build.gradle.kts:7`, `onemoney/app/build.gradle.kts:10`: Google services 4.4.4, Crashlytics plugin 3.0.8, Firebase BoM 34.18.0. Firebase подключён только через playImplementation(platform(...)) + firebase-crashlytics; Analytics не добавлен. Sentry Gradle plugin удалён, SDK перенесён в personalImplementation.
- `onemoney/app/build.gradle.kts:218`: processPersonal*GoogleServices и personal Crashlytics tasks отключены; personal не требует google-services.json.
- `onemoney/app/src/play/java/org/syalosovetskyi/onemoney/CrashReporter.kt:8`: collection включается только при !BuildConfig.DEBUG; debug recordException отключён. SDK default collection выключен в `src/play/AndroidManifest.xml:4`, до Application.onCreate тоже. Ошибки самого репортера логируются и не заменяют исходную ошибку новым падением.
- `onemoney/app/src/personal/java/org/syalosovetskyi/onemoney/CrashReporter.kt:8`: прежняя условная инициализация Sentry по DSN и прежние options. DSN BuildConfig field и sentry auto-init metadata находятся только в personal. В main/play Kotlin и manifest нет ссылок/адресов GlitchTip/Sentry.
- Пойманные исключения с warning/error в repositories, workers, ViewModels, color parsing и UI теперь проходят через CrashReporter; severity и сообщения логов сохранены. В release play дополнительно вызывается recordException. Сообщения логов без Throwable не превращались в искусственные исключения.
- `src/testPlay/.../CrashReporterTest.kt`: collection policy и передача того же Throwable для warning/error; `src/testPersonal/.../CrashReporterTest.kt`: поведение с необязательным DSN. Debug прогон проверяет отключённую отправку; release-ветка требует отдельного release unit test прогона.
- `onemoney/app/src/play/google-services.json` оставлен как предоставлен пользователем. SHA256: `99ce77a87ba0d1d0c152c97128c6932b98eabe2f3fb1eb6feb2ca38f51d7f4b5`.
- `store/play/data-safety.md:1`: Crash logs + Diagnostics: collected, not shared (Google — поставщик сервиса), encrypted in transit, stability/diagnostics.
- `.github/workflows/ci.yml:41` и `.github/workflows/build.yml:35`: debug unit tests для обоих flavors, flavor-aware coverage/APK paths. Существующий GitHub Release продолжает публиковать personal APK с прежним обязательным DSN; Play публикация не добавлялась.

Документация SDK/плагинов: [Firebase Android Crashlytics setup](https://firebase.google.com/docs/crashlytics/android/get-started), [Firebase Android setup](https://firebase.google.com/docs/android/setup). Сведения для декларации: [Firebase Play data disclosure](https://firebase.google.com/docs/android/play-data-disclosure).

## Матрица и новые тесты

Обновлена `.context/coverage-matrix-2673.md`: **9 из исходных 42** строк закрыты по реализованным сценариям. **33 остаются незакрытыми**: 32 частично и 1 без отдельного теста (NavGraph). В каждой строке перечислен конкретный остаток. Старые общие ссылки на блокировку Gradle удалены; запрет запуска не выдаётся за причину отсутствия реализации.

Всего матрица: 108 строк; 51 «Добавлен», 23 «Есть тест», 32 «Частично», 1 «Пробел», 1 «Исключение». Подтверждённых успешным прогоном строк этого раунда: 0.

Новые файлы содержат 36 @Test методов; ещё 1 добавлен в WorkerBehaviorTest (37 новых методов в исходниках, не число выполненных тестов):

- PreferenceRepositoriesTest: настоящий изолированный DataStore, defaults/update/income budget, invalid enum fallback; курсы UAH/cache/refresh/freshness/network failure/broken JSON. Для изоляции добавлены internal DataStore constructors и fetch seam с сохранением Hilt Context constructor.
- ComponentInteractionTest: 27 Compose-сценариев для confirm/cancel/blank input, description/balance, currency/category/transfer pickers, reset, repeat/reminder, icon picker, Overview detail, keypad, month nav, chip gestures, settings radio, budget settings, quick transfer validation, transaction delete confirmation, search callbacks, category editor tabs, submit форм счёта/категории.
- ComponentRulesTest: 4 сценария category visual fallback, calendar boundaries, repeat/reminder fallback и period badges.
- ThemeSelectionTest: смена light/dark/custom accent и semantic tokens после recomposition.
- Flavor CrashReporterTest: по одному сценарию для каждой обёртки.
- WorkerBehaviorTest: notifications disabled не публикует уведомление, но планирует следующее; enabled-тест теперь обязательно требует реальное уведомление вместо условной проверки пустого списка.

## Что осталось

Полное закрытие всех 42 строк **не сделано**. Матрица честно сохраняет оставшиеся пробелы: HTTP happy path синка с записью в Room, DocumentsProvider для успешного SAF backup/rename/cleanup, Hilt navigation/back stack integration, полный CRUD/drag/calendar flow и дополнительные ветки отдельных picker/screens. Добавленные interaction-тесты проверяют конкретные действия; они не заменяют отсутствующие end-to-end сценарии. Это незавершённая реализация покрытия, а не утверждение о невозможности тестирования этих компонентов.

Не выполнены компиляция, dependency resolution, APK inspection и проверка поступления события в Firebase console. Для последних нужны пользовательский прогон и установленный release APK; локальный запрет Gradle соблюдён. Существующая проверка release signing в Gradle сохранена: release tasks требуют SIGNING_* / signing.*.

## Проверки, выполненные здесь

- `git diff --check`: exit 0.
- Python stdlib: TOML version catalog, XML трёх manifest и JSON Firebase разбираются; package name в конфиге совпадает с applicationId.
- Статический поиск: main/play Kotlin/XML без Sentry/GlitchTip/DSN; Firebase dependencies только play, Analytics отсутствует.
- google-services.json не редактировался.

Для пользовательского прогона из onemoney:

```sh
./gradlew :app:testPersonalDebugUnitTest :app:testPlayDebugUnitTest :app:assemblePersonalDebug :app:assemblePlayDebug
```

При настроенной release signing отдельно: `:app:testPlayReleaseUnitTest :app:assemblePlayRelease` (проверяет включённую release-передачу в unit wrapper test; доставка в консоль всё равно требует запуска APK).

## План коммитов

Группы ниже используют целые файлы, без необходимости делить hunks. Порядок сохраняет зависимость общего кода от CrashReporter.

### 1. Flavors, crash reporting и CI

Сообщение: `feat: isolate personal crash reporting and add Play Crashlytics`

- `.github/workflows/build.yml`
- `.github/workflows/ci.yml`
- `onemoney/app/build.gradle.kts`
- `onemoney/app/src/main/AndroidManifest.xml`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/MoneyIQApp.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/data/repository/CurrencyRatesRepository.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/data/repository/SettingsRepository.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/data/repository/TxOverrideRepository.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/ui/accounts/AccountSheets.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/ui/categories/CategoriesWidgets.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/ui/components/calculator/CalcKeypad.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/ui/data/DataViewModel.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/ui/transactions/AddTransactionScreen.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/ui/transactions/TransactionDetailSheet.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/ui/transactions/TransactionViewModel.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/util/ColorHex.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/workers/DriveBackupWorker.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/workers/MonoFlowSyncWorker.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/workers/RepeatTransactionWorker.kt`
- `onemoney/app/src/personal/AndroidManifest.xml`
- `onemoney/app/src/personal/java/org/syalosovetskyi/onemoney/CrashReporter.kt`
- `onemoney/app/src/play/AndroidManifest.xml`
- `onemoney/app/src/play/google-services.json`
- `onemoney/app/src/play/java/org/syalosovetskyi/onemoney/CrashReporter.kt`
- `onemoney/app/src/testPersonal/java/org/syalosovetskyi/onemoney/CrashReporterTest.kt`
- `onemoney/app/src/testPlay/java/org/syalosovetskyi/onemoney/CrashReporterTest.kt`
- `onemoney/build.gradle.kts`
- `onemoney/gradle/libs.versions.toml`
- `store/play/data-safety.md`

### 2. Регрессии Robolectric, фокуса, сортировки и Overview

Сообщение: `fix: restore category focus safely and correct locale sorting tests`

- `onemoney/app/src/main/java/org/pixelrush/moneyiq/ui/categories/CategoryFormSheets.kt`
- `onemoney/app/src/main/java/org/pixelrush/moneyiq/ui/categories/CategorySheets.kt`
- `onemoney/app/src/test/java/org/pixelrush/moneyiq/data/db/ParentUpsertTest.kt`
- `onemoney/app/src/test/java/org/pixelrush/moneyiq/ui/ScreenRenderTest.kt`

### 3. Дополнительное покрытие и честная матрица

Сообщение: `test: extend Zerno interaction coverage and document remaining gaps`

- `.context/coverage-matrix-2673.md`
- `.context/zerno-2673-round2.md`
- `onemoney/app/src/test/java/org/pixelrush/moneyiq/data/repository/PreferenceRepositoriesTest.kt`
- `onemoney/app/src/test/java/org/pixelrush/moneyiq/ui/ComponentInteractionTest.kt`
- `onemoney/app/src/test/java/org/pixelrush/moneyiq/ui/ComponentRulesTest.kt`
- `onemoney/app/src/test/java/org/pixelrush/moneyiq/ui/ThemeSelectionTest.kt`
- `onemoney/app/src/test/java/org/pixelrush/moneyiq/workers/WorkerBehaviorTest.kt`
