# MoneyIQ: проверка перед публикацией — 2026-11-04

Работа частично завершена: внесены 8 исправлений, добавлены 13 JVM-тестов.
Компиляция и тесты не состоялись: wrapper не смог скачать Gradle из-за запрета
сети. Коммиты создать не удалось: служебный Git-каталог worktree доступен только
для чтения; запрос расширенных прав отклонён. Push не выполнялся.

Пути ниже относительно `onemoney/app/src/main/java/org/pixelrush/moneyiq/`.
Номера строк — в рабочем дереве после правок. «Да» означает исправлено в исходнике,
но не подтверждено запуском JVM-тестов.

## Подтверждённые баги

| № | Серьёзность | Файл:строка | Сценарий | Ожидаемое → фактическое до правки | Исправлен |
| --- | --- | --- | --- | --- | --- |
| 1 | Критическая | data/db/dao/AccountDao.kt:43 | Инкрементальный sync обновляет существующий счёт, но не присылает старые операции | Сохранить историю и обе ссылки перевода → REPLACE удаляет исходящие операции через CASCADE и обнуляет входящие ссылки через SET NULL | Да: single/bulk Upsert |
| 2 | Высокая | data/db/dao/CategoryDao.kt:23 | Sync присылает существующую категорию без старых операций | Сохранить назначения категорий → REPLACE обнуляет categoryId у старых операций | Да: single/bulk Upsert |
| 3 | Критическая | util/BackupSerializer.kt:82,155 | Экспорт/восстановление перевода 820 UAH → 20 USD, затем удаление | Снять с получателя 20 USD → поле toAmount терялось, снималось 820 USD | Да |
| 4 | Высокая | data/repository/TransactionRepository.kt:124 | У перевода 820 UAH → 20 USD заменить источник на USD-счёт, оставив amount=820 | Зачислить 820 USD → keepsCredit сохранял 20 USD | Да |
| 5 | Критическая | workers/RepeatTransactionWorker.kt:162,166 | Наступает повтор TRANSFER/REPAY с amount=820, toAmount=20 | Зачислить сохранённые 20 → worker зачислял 820; последующее удаление снимало только 20 | Да |
| 6 | Средняя | ui/reports/ReportsViewModel.kt:71,75 | ViewModel создан при миллисекундах .456; операции на первом .000 и последнем .999 миллисекундах месяца | Включить обе → границы были .456, обе операции исключались | Да |
| 7 | Средняя | data/repository/SelectedMonthRepository.kt:109 | Europe/Berlin: 24–25 октября 2026 включительно; либо 28 марта 00:00–30 марта 00:00 включительно | 2 / 3 календарных дня → фиксированные 24 часа давали 3 / 2 | Да |
| 8 | Средняя | util/CsvExporter.kt:51 | CSV-экспорт 1234.56 при Locale.GERMANY | 7 столбцов и одна сумма → 1234,56 становилось двумя столбцами, строка имела 8 | Да |
| 9 | Высокая | data/repository/TransactionRepository.kt:42 | Первый запуск офлайн, нет курса USD; перевод 820 UAH на USD | Не создавать некорректное зачисление или запросить курс → зачисляется 820 USD; предупреждение есть только в Log.w | Нет: явное поведение ADR-073, нужен согласованный UX ошибки во всех вызывающих путях |
| 10 | Критическая | data/repository/TransactionRepository.kt:79–92 | Ошибка/остановка процесса после вставки операции или после списания первой стороны | Атомарно записать операцию и оба баланса → независимые DAO-коммиты оставляют частичную операцию; update/delete имеют тот же риск | Нет |
| 11 | Критическая | ui/data/DataViewModel.kt:151–158 | Восстановить JSON с операцией, ссылающейся на отсутствующий счёт | Отклонить до изменения данных либо откатить всю замену → прежние таблицы уже удалены, затем insertTransactions падает по FK | Нет |
| 12 | Высокая | data/db/dao/TransactionDao.kt:73,81; ui/budget/BudgetViewModel.kt:87 | В месяце расходы 100 UAH и 100 USD при курсе 41 | 4200 UAH либо отдельные суммы по валютам → SUM(amount)=200; бюджеты также сравнивают с сырыми суммами, игнорируя currencyCode | Нет |
| 13 | Высокая | ui/accounts/AccountsScreen.kt:453–468 | Во вкладке «Мої фінанси» есть 100 UAH и 100 USD | Итог в общей валюте → активы суммируются как 200, несмотря на конвертацию в AccountRepository | Нет |
| 14 | Средняя | data/db/dao/AccountDao.kt:28; ui/accounts/AccountsScreen.kt:283 | Баланс 0.30, два расхода 0.10 и 0.20 | Точный 0 → Double даёт -2.7755575615628914e-17, условие balance<0 окрашивает нулевой баланс как отрицательный | Нет: нужна согласованная денежная точность, включая валюты и миграцию |
| 15 | Высокая | ui/budget/BudgetViewModel.kt:87–88 | У категории budgetPeriod=WEEKLY, бюджет 100; выбран месячный период | Учесть период бюджета → budgetPeriod нигде не участвует в расчёте, месячный итог сравнивается с 100 | Нет |
| 16 | Высокая | ui/transactions/TransactionViewModel.kt:102 | Передать amount="NaN" или "1e309" в save (публичный setter, импортируемые/внешние данные нуждаются в такой же защите) | Отклонить не конечное число → toDoubleOrNull принимает NaN/Infinity, проверка <=0 пропускает их в репозиторий | Нет; достижимость именно с обычной цифровой клавиатуры не утверждается |
| 17 | Средняя | ui/components/calculator/CalcState.kt:51 | Ввести 999999999999 и многократно умножать на такое же число до переполнения, нажать = | Ошибка ввода без падения → result=Infinity, toBigDecimal бросает NumberFormatException | Нет |
| 18 | Высокая | workers/RepeatTransactionWorker.kt:59–61 | Worker остановлен после вставки повтора, до clearNextRepeatDate | Один повтор → исходный и новый экземпляры остаются due; повторный запуск может создать дубликаты и повторно изменить балансы | Нет: требуется общая транзакция и идемпотентность |
| 19 | Высокая | ui/transactions/TransactionViewModel.kt:120 | Открыть редактирование повторяющейся операции и сохранить заметку | Сохранить repeatMode/reminderMode/nextRepeatDate/createdAt → новый TransactionEntity использует значения по умолчанию и отключает повтор | Нет |

SQLite-воспроизведение с PRAGMA foreign_keys=ON подтвердило №1 и №2.
Независимое вычисление дат zoneinfo подтвердило №7; это не запуск Kotlin-кода.
№10/11/18 следуют из порядка независимых DAO-записей; сбой процесса на устройстве
не инъектировался.

## Дополнительные ограничения аудита

- Деление на ноль возвращает pendingVal (например, 10÷0=10), а не ошибку. Такую
  семантику уже закрепляют существующие тесты; отдельно нужен продуктовый выбор.
- Повторная десятичная запятая и две дробные цифры ограничены CalcState.
  Аппаратная точка преобразуется обработчиком клавиатуры в запятую.
- AccountDao.deleteAccount вызывает CASCADE для исходящих операций и SET NULL
  для входящих; CategoryDao.deleteCategory обнуляет categoryId. Это соответствует
  DB_SCHEMA; не объявлялось багом без другой спецификации удаления.
- Кредитный лимит не участвует в изменении баланса: он отдельное поле. Контракт
  запрещать траты сверх лимита в прочитанных правилах не установлен, поэтому
  отсутствие запрета не выдано за подтверждённый баг.
- В DatabaseModule.kt:27 есть fallbackToDestructiveMigration(dropAllTables=true).
  Цепочка 1→34 присутствует целиком, но будущий пропуск миграции удалит данные.
  Это риск, а не доказательство уже случившейся потери при обычном 33→34.
- Room exportSchema=false. Полная проверка апгрейдов со всех версий не выполнена.
- Утечки объектов и зависания main не подтверждены профилированием. Есть
  синхронный запрос имени SAF-папки из loadState на main (DataViewModel.kt:79–81);
  медленный DocumentsProvider потенциально блокирует UI.

## Google Play — только проверка, без правок

1. **Privacy policy / Data safety:** в проверенных ресурсах и UI не найдена ссылка
   на privacy policy. Есть финансовые данные, токен sync и опциональный Sentry.
   До публикации нужна доступная политика внутри приложения и в listing, плюс
   корректная декларация фактической конфигурации sync/crash-reporting.
   [Официальная политика User Data](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).
   Настройки и заполнение Play Console проверить не удалось.
2. **Брендинг:** app/build.gradle.kts:23 задаёт applicationId
   org.syalosovetskyi.onemoney; app/src/main/res/values/strings.xml:6 — onemoney.
   Это не MoneyIQ. Сам пакет технически допустим, но выбор идентификатора,
   владение и соответствие listing нужно решить до первой публикации.
3. **Чужие APK:** в settings.gradle.kts подключён только :app. Нет sourceSets,
   assets/res или Gradle-задач, подключающих корневые APK и apks_extracted.
   Среди app/src/main/res и файлов apks_extracted не найдено одинаковых SHA-256.
   Значит прямое включение и точные копии не обнаружены; изменённые изображения,
   переведённые строки, сходство дизайна и происхождение остальных ресурсов так
   не исключаются. Иконка просмотрена: белая копилка на фиолетовом фоне.
   Лицензии/права на графику не доказаны.
   [Политика Intellectual Property](https://support.google.com/googleplay/android-developer/answer/9888072?hl=en).
4. **Сеть:** util/AppConstants.kt:6 — публичный HTTPS API НБУ;
   ui/data/DataWidgets.kt:96 — placeholder личного monoflow.ibotz.fun.
   Реальный sync URL и token берутся из настроек, worker добавляет /api/sync.
   MoneyIQApp.kt:38–54 включает Sentry только при непустом GLITCHTIP_DSN;
   скриншоты, view hierarchy и default PII выключены.
5. **Секреты:** эвристический поиск строковых token/password/secret/api_key в
   отслеживаемых текстовых файлах не нашёл жёстко заданного секрета.
   app/build.gradle.kts:107 задаёт release DEBUG_MONOFLOW_TOKEN пустым;
   debug берёт его из некоммитимого local.properties. DSN берётся из local
   properties или окружения. Значения секретов не печатались; история Git,
   бинарные артефакты и внешние CI secrets не сканировались.
6. **Разрешения:** main manifest:4–7 — USE_BIOMETRIC, RECEIVE_BOOT_COMPLETED,
   POST_NOTIFICATIONS, INTERNET. Нет READ_SMS, контактов, location и широкого
   storage permission. Релизный merged manifest не получен.
7. **SDK:** targetSdk=36, compileSdk=36 (app/build.gradle.kts:20,25).
   Target соответствует найденному текущему требованию API 36:
   [официальные требования](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).
   В данном /opt/android-sdk/platforms есть android-35 и android-37.0, нет
   android-36: после разблокировки Gradle потребуется SDK Platform 36.
8. Подписанный release AAB, R8, 16-KB-совместимость нативных библиотек зависимостей,
   pre-launch report и результат проверки Google Play не проверены.

## Новые JVM unit-тесты — 13 методов в 7 файлах

База путей: app/src/test/java/org/pixelrush/moneyiq/.

- util/BackupSerializerTest.kt (+2): round-trip toAmount; старый JSON без поля.
- data/repository/TransactionRepositoryTest.kt (+2): смена валюты источника;
  правка заметки сохраняет историческое зачисление при изменившемся курсе.
- data/repository/SelectedMonthRepositoryTest.kt (+3): весенний DST, осенний DST,
  миллисекундные границы месяца с переходом DST.
- ui/reports/ReportsViewModelTest.kt (+1): обе крайние миллисекунды месяца.
- util/CsvExporterTest.kt (новый, 1): decimal-comma локаль сохраняет 7 столбцов.
- workers/RepeatTransactionBalanceTest.kt (новый, 2): TRANSFER/REPAY с toAmount;
  совместимость старого повтора без toAmount.
- data/db/ParentUpsertTest.kt (новый, 2): настоящая Room in-memory под Robolectric,
  история и обе ссылки перевода переживают single/bulk upsert счетов; назначения
  категории переживают single/bulk upsert категории.

Каждый фикс связан с регрессионной проверкой. Фактическое падение до фикса и
прохождение после фикса не подтверждены: JVM-suite не стартовал.
В исходниках теперь 270 @Test методов; это НЕ число выполненных тестов.

## Проверки и блокировки

- `git diff --check`: PASS.
- SQLite/zoneinfo воспроизведения: PASS (подтверждены исходные дефекты).
- `./gradlew`: Permission denied (исполняемый бит отсутствует в этом checkout).
- `bash gradlew :app:testDebugUnitTest --console=plain`: не удалось создать
  lock в /root/.gradle; запрос расширенных прав отклонён.
- Повтор с GRADLE_USER_HOME внутри worktree: wrapper попытался скачать 8.11.1;
  java.net.SocketException: Operation not permitted. Ни зависимости приложения,
  ни компиляция, ни тесты не были достигнуты.
- Последняя фактическая строка вывода wrapper:
  `at org.gradle.wrapper.GradleWrapperMain.main(SourceFile:67)`.
  Строки Gradle с числом тестов нет. Выполнено тестов: 0.
  Полный вывод: review-2673-gradle.log.
- local.properties создан с sdk.dir=/opt/android-sdk, игнорируется Git.
- Коммиты test(...) и fix(...) не созданы: запись index.lock вне writable root
  запрещена; отдельный запрос на Git-запись отклонён. Ничего не отправлялось.
