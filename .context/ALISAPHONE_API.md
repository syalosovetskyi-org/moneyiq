# Alisaphone → Zerno personal: локальний Messenger API

Реалізація #2673, 2026-11-08. Kotlin/Room/Robolectric тести написані, але не запускались.
API існує тільки у `personal`; у manifest `play` сервіс відсутній. Мережа для команд
не потрібна. Курси валют беруться лише з локального кешу, команди не завантажують їх.

## Підключення та довіра

- applicationId: `org.syalosovetskyi.onemoney`
- component: `org.syalosovetskyi.onemoney/org.syalosovetskyi.onemoney.alisaphone.AlisaphoneService`
- action: `org.syalosovetskyi.onemoney.ALISAPHONE_API`
- exported bound `Service`, транспорт `android.os.Messenger`, без started/foreground service.
- Викликати `bindService()` з **явним component**, зберігати підключення до відповіді.
- Дозволений пакет: `com.alisaphone`. UID береться з `Message.sendingUid`, не з Bundle
  і не з `Binder.getCallingUid()` усередині Handler. `PackageManager.getPackagesForUid`
  має містити дозволений пакет; усі його поточні підписи мають бути закріплені.
- SHA-256 — від DER-байтів `Signature.toByteArray()`. На API 28+ перевіряються
  `SigningInfo.apkContentsSigners`, на API 26–27 — `GET_SIGNATURES`.
- `BuildConfig.ALISAPHONE_CERT_SHA256` є тільки у personal. Джерело:
  env `ALISAPHONE_CERT_SHA256`, інакше `local.properties`: `alisaphone.certSha256`.
  Значення: один або декілька 64-символьних SHA-256 hex через кому; регістр і двокрапки
  неважливі. Значення має містити хоча б один валідний відбиток, інакше всі команди
  отримують `caller_not_configured`. Піни перевіряються і в debug, обхід відсутній.
- Чужий UID/пакет/підпис → `caller_denied` і warning із UID, без фінансових даних.
  `Message.sendingUid` вручну клієнтом не встановлювати: його передає Messenger.

## Запит і відповідь

`Message.data`: `Bundle`, обов'язковий `cmd: String`; параметри нижче на верхньому рівні.
`Message.replyTo`: обов'язковий Messenger клієнта. `what`, `arg1`, `arg2`, `obj`
не використовуються. Відсутній `replyTo` → warning, команда не виконується.

Відповідь — `Message.data`:

| Ключ | Тип | Значення |
|---|---|---|
| `ok` | Boolean | Чи виконано команду |
| `error` | String? | Код помилки; при успіху ключ відсутній |
| `result` | String | JSON-об'єкт; при очікуваній помилці `{ "message": "пояснення" }`, при internal_error `{}` |

Клієнт має обробляти `ok=false` та disconnect/timeout. Пропуск не означає успіх.
Невідомі для команди ключі Bundle повертають `invalid_params`, а не ігноруються.
ID у JSON — **signed 64-bit Long**, не Int і не Double. Для кореляції використовуйте
окремий reply Messenger на запит або один запит за раз: протокол не повертає requestId.
Команди сервісу серіалізовані Mutex. Після відключення останнього клієнта незавершена
команда може скасуватися; не відв'язуйтесь раніше відповіді. Автоматично повторювати
`add_transaction` після timeout не можна: протокол наразі не має idempotency key.

## Команди

### add_transaction

| Параметр | Тип | Правило |
|---|---|---|
| `type` | String | Обов'язково `expense` або `income` |
| `amount` | Double | Обов'язково, скінченне число строго більше 0; `putDouble`, не рядок/Int |
| `currency` | String? | За замовчуванням валюта рахунку; при відмінності потрібні обидва кешовані курси |
| `category` | String? | Не задано — без категорії; задано — точний пошук Unicode ignore-case серед неархівованих категорій потрібного типу |
| `account` | String? | Ім'я ignore-case; за замовчуванням `isDefault`, інакше перший за DAO sortOrder/name, як у швидкому введенні |
| `note` | String? | Не задано — порожній рядок |
| `date` | String? | ISO `YYYY-MM-DD` (початок дня у часовому поясі пристрою), або ISO timestamp з offset/`Z`; за замовчуванням зараз |

Переданий рядок не може бути порожнім/blank або довшим за 4096 символів; необов'язкові
порожні поля краще не передавати. Currency нормалізується до uppercase.

Категорії користувача шукаються за їх збереженим ім'ям. Для стандартних категорій є
uk/ru/en аліаси: Продукти/Продукты/Groceries/Food, Ресторація/Ресторация/Dining, Ресторани/Рестораны/Restaurants,
Дозвілля/Досуг/Leisure, Транспорт/Transport, Здоров'я/Здоровье/Health,
Подарунки/Подарки/Gifts, Сім'я/Семья/Family, Покупки/Shopping, Робота/Работа/Work,
Зарплата/Salary, Фриланс/Фріланс/Freelance, Інше/Другое/Other. Також підтримуються
uk/ru/en аліаси всіх стандартних підкатегорій (Таксі/Такси/Taxi, Пальне/Топливо/Fuel,
Оренда/Аренда/Rent тощо); AliExpress збігається за власним ім'ям. Спочатку шукається
точне ім'я без регістру, аліаси застосовуються лише за відсутності точного збігу.
Невідоме ім'я не створює нову категорію: `category_not_found`, `result.message`
містить до п'яти найближчих імен за edit distance. Кілька збігів → `category_ambiguous`.
Неоднозначний рахунок → `account_ambiguous`.

Запис виконується через `TransactionRepository.addTransaction` усередині Room
`withTransaction`: операція і зміна балансу атомарні. Звичайний запис має
`repeatMode=NEVER`, `reminderMode=NEVER`, як ручне введення без повторів.
Місцеві нові операції не відправляються на mono-flow: наявна черга підтримує лише
правки вже відомих серверу операцій. API не вигадує окремий шлях синхронізації.

Результат (сума і balance_after у валюті **рахунку**, вже після конвертації):

```json
{"id":-123456789012345,"amount":200.0,"currency":"UAH","category":"Продукти","account":"Wallet","balance_after":-200.0}
```

### balance

`account?: String` — той самий пошук за ім'ям. Без account: усі рахунки у відповіді,
`total` включає лише `includeInTotal=true`. З account: total саме цього рахунку,
навіть коли він виключений із загального підсумку.

```json
{"currency":"UAH","total":-100.0,"complete":false,"missing_rates":["RUB"],"accounts":[{"id":1,"account":"Wallet","amount":-100.0,"currency":"UAH","include_in_total":true},{"id":2,"account":"Old wallet","amount":-200.0,"currency":"RUB","include_in_total":true}]}
```

UAH — поточна валюта міжрахункових звітів застосунку. `complete=false` означає
**частковий** total, невідомі валюти перелічені у `missing_rates`. Не озвучуйте його
як повний баланс. Нульовий рахунок без курсу не робить суму неповною.

### recent

`limit?: Int`, 1..50, default 10. Сортування за date DESC, як у репозиторії UI.

```json
{"transactions":[{"id":-123456789012345,"type":"expense","amount":200.0,"currency":"UAH","account":"Wallet","category":"Продукти","note":"магазин","date":"2026-11-08T12:00:00Z"}]}
```

Суми у власній валюті рахунку, `category` може бути JSON null.

### month_summary

`month?: String`, строгий `YYYY-MM`, default поточний місяць у часовому поясі
пристрою. Включає expense/income, без переказів/боргів. Категорія null має окрему групу.

```json
{"month":"2026-11","currency":"UAH","complete":true,"missing_rates":[],"categories":[{"type":"expense","category":"Продукти","amount":200.0,"complete":true}]}
```

### open

`screen: String` обов'язково: `accounts|categories|operations|budget|overview`.
Intent у MainActivity: extra `zerno.screen`, прапорці NEW_TASK/CLEAR_TOP/SINGLE_TOP.
Працює і для холодного запуску, і через onNewIntent; повторне відкриття тієї самої
вкладки теж обробляється. При явному відкритті budget прихована вкладка стає доступною
для цього запиту, налаштування користувача не переписується.
Результат: `{"screen":"operations"}` означає доставлення Intent, а не доказ рендерингу.
Android обмежує запуск Activity з фону: клієнт повинен мати право BAL. На Android 14+
передавайте BIND_ALLOW_ACTIVITY_STARTS, коли прив'язуєтесь із дозволеного foreground
контексту; сам прапорець не скасовує системних обмежень. Потрібна перевірка на телефоні.

### undo_last

Без параметрів. Видаляє **одну останню** створену цим API операцію через
`TransactionRepository.deleteTransaction` з відкатом балансу в Room transaction.
Не видаляє останню ручну операцію і не переходить до старішої API-операції при
повторному виклику. Результат: `{"id":-123456789012345,"balance_after":0.0}`.

API резервує випадкові від'ємні ID, не перетинаючи автоінкремент/серверні ID.
Приватні SharedPreferences тримають ID та fingerprint повного оригіналу між
перезапусками сервісу. Відсутня операція → `nothing_to_undo`, змінена після API-запису
→ `undo_conflict`; ручні правки не відкидаються. Повторний undo бачить відсутній рядок.
Покажчик undo не входить до JSON backup; відновлення на іншому пристрої не надає
права undo за самим лише від'ємним ID. Збій між commit SharedPreferences та commit
Room може втратити доступ до попереднього undo, але не дозволяє видалити чужу операцію:
потрібні ID та повний fingerprint. Фінансовий запис і баланс при SQL-збої відкочуються.

## Помилки

| Код | Значення |
|---|---|
| `caller_not_configured` | У personal не закріплено жодного валідного сертифіката |
| `caller_denied` | UID/пакет/поточний підпис не довірені |
| `unknown_command` | Невідомий cmd |
| `invalid_params` | Немає обов'язкового поля, неправильний тип/діапазон/дата/screen |
| `account_not_found`, `account_ambiguous` | Неможливо однозначно вибрати рахунок |
| `category_not_found`, `category_ambiguous` | Неможливо однозначно вибрати категорію |
| `rate_unavailable` | Неможливо перевести amount у валюту рахунку без локального курсу |
| `nothing_to_undo` | Немає останньої API-операції або її вже видалено |
| `undo_conflict` | Операцію відредагували після створення |
| `storage_error` | Не вдалося зберегти undo ownership або колізія випадкового ID; DB відкочується |
| `internal_error` | Неочікуваний збій; warning у Zerno, без фінансових даних у відповіді |

## Приклад клієнта Kotlin (один запит)

Викликати з foreground Activity/головного потоку. Для Android 11+ додайте в manifest
клієнта `<queries><package android:name="org.syalosovetskyi.onemoney" /></queries>`.
`onResult` повинен перевірити `ok`; клієнт також має реалізувати timeout та disconnect.

```kotlin
fun addExpense(activity: Activity, onResult: (Bundle) -> Unit) {
    lateinit var connection: ServiceConnection
    val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
        onResult(message.data) // ok/error/result(JSON), включно з відмовою авторизації
        activity.unbindService(connection)
        true
    })
    connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val request = Message.obtain().apply {
                replyTo = reply
                data = Bundle().apply {
                    putString("cmd", "add_transaction")
                    putString("type", "expense")
                    putDouble("amount", 200.0)
                    putString("category", "groceries")
                    putString("note", "магазин")
                    // Не задано account/currency/date → default account/його валюта/зараз.
                }
            }
            try { Messenger(binder).send(request) }
            catch (failure: RemoteException) {
                Log.w("Alisaphone", "Zerno request failed", failure)
                activity.unbindService(this)
                onResult(Bundle().apply { putBoolean("ok", false); putString("error", "disconnected") })
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            onResult(Bundle().apply { putBoolean("ok", false); putString("error", "disconnected") })
        }
    }
    val intent = Intent("org.syalosovetskyi.onemoney.ALISAPHONE_API").apply {
        component = ComponentName("org.syalosovetskyi.onemoney",
            "org.syalosovetskyi.onemoney.alisaphone.AlisaphoneService")
    }
    val flags = Context.BIND_AUTO_CREATE or
        (if (Build.VERSION.SDK_INT >= 34) Context.BIND_ALLOW_ACTIVITY_STARTS else 0)
    if (!activity.bindService(intent, connection, flags)) {
        onResult(Bundle().apply { putBoolean("ok", false); putString("error", "service_unavailable") })
    }
}
```

`disconnected` і `service_unavailable` у прикладі — локальні помилки клієнта, не коди
від сервісу Zerno. Для production-клієнта звільняйте binding також за timeout,
скасуванням і onNullBinding; після невідомого результату add спочатку перевірте recent.
