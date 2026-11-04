# Бриф: OneMoney → Zerno, полное покрытие тестами, оставшиеся баги (serg/tasks#2673)


**Сначала прочитай CLAUDE.md в корне и `.context/SYSTEM_OVERVIEW.md`, `.context/DB_SCHEMA.md`, `.context/ADR_LOG.md`, `.context/ui/*`. Соблюдай их правила: миграции Room, `categoryIconFor()`, `luminance()`, `bug-log.md` (после каждого нетривиального фикса добавляй запись). Команды в CLAUDE.md написаны для PowerShell, здесь Linux bash.**
Работай в этом worktree (ветка codex/moneyiq-review-2673). Проект Android лежит в `onemoney/`. Прошлый заход (коммит 6ab327b) исправил 8 багов учёта, подробности в `.context/review-2673.md`. Коммить каждую фазу отдельно, небольшими коммитами. Сети нет. Gradle запускай через `bash ./gradlew --offline` (wrapper без +x). Если офлайн не собирается, напиши об этом прямо и не выдавай «тесты зелёные».

Правило проекта: ни одно исключение не глушится молча. У каждого catch должен быть лог уровня warning или выше, отправка в GlitchTip или комментарий с объяснением, почему молчание здесь осознанно.

## Фаза A. Ребрендинг на Zerno (Серж выбрал)
Источник: макет https://claude.ai/artifact/STnmKhnJJg67y7mfh8Miv4. Нужные значения продублированы ниже.
1. Имя приложения: «Zerno»; подпись в шапке меню: «Особистий бюджет». Убери все видимые пользователю «1Money» и «OneMoney»: strings, label в манифесте, шапка выдвижного меню, логотип «1» с градиентом `#4361EE→#7B2FBE` в MainScreen.kt примерно на строках 375–390. Вместо логотипа поставь иконку Zerno + имя + подпись.
2. Адаптивная иконка. Пиши векторами, а старые mipmap-png 1Money удали.
   - `drawable/ic_launcher_foreground.xml`:
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
       <group android:pivotX="54" android:pivotY="54" android:rotation="20">
         <path android:fillColor="#FFDDB1" android:pathData="M52,32 C41,42 40,58 52,76 Z"/>
         <path android:fillColor="#FFDDB1" android:pathData="M56,32 C67,42 68,58 56,76 Z"/>
       </group>
     </vector>
     ```
   - `drawable/ic_launcher_background.xml`: сплошной фон `#614004` (path `M0,0h108v108h-108z`).
   - `mipmap-anydpi-v26/ic_launcher.xml` и `ic_launcher_round.xml`: adaptive-icon со слоями background, foreground и monochrome (monochrome = тот же foreground).
3. Палитра M3 (seed `#B07A1A`, TonalSpot). Замени дефолтный акцент `#4D5C92` и строку 51 в Theme.kt.
   - Светлая тема:
     - primary `#7C581C` / onPrimary `#FFFFFF`;
     - primaryContainer `#FFDDB1` / onPrimaryContainer `#291800`;
     - secondary `#6F5B40`;
     - tertiary `#855239`;
     - surface `#FFF8F4` / onSurface `#201B15`;
     - onSurfaceVariant `#4F4538`;
     - outline `#817566`.
   - Тёмная тема:
     - primary `#EFBE79` / onPrimary `#442B00`;
     - primaryContainer `#614004` / onPrimaryContainer `#FFDDB1`;
     - secondary `#DDC3A1`;
     - tertiary `#FBB798`;
     - surface `#17130D` / onSurface `#EBE1D6`;
     - onSurfaceVariant `#D3C4B3`;
     - outline `#9C8F7F`.
   - Токены денег (светлая / тёмная): income `#1B6B3A` / `#6FD08C`, expense `#B3261E` / `#FFB4A9`, transfer `#1F5FA6` / `#A8C8FF`, debt `#8A3FA0` / `#E9B3F5`. Положи их в одно место (например `MoneyColors` через CompositionLocal) и используй везде вместо хардкода. Суммы выводи со знаком «+»/«−»: цвет не должен быть единственным носителем смысла.
   - Если пользовательский выбор акцента в настройках существует, сохрани его, но по умолчанию ставь Zerno.
4. NavIcons.kt: шесть пар значков 1Money замени на Material Icons (Outlined):
   - месяц назад и вперёд → `Icons.AutoMirrored.Outlined.KeyboardArrowLeft/Right` или ChevronLeft/Right;
   - профиль → AccountCircle;
   - аватар → Person;
   - редактирование → Edit;
   - настройки → Settings.
   Удали векторные ресурсы 1Money, которые после этого никто не использует.
5. Удали из дерева закоммиченные APK 1Money (`git ls-files | grep -i '\.apk$'`) и добавь `*.apk` в .gitignore.
6. НЕ меняй applicationId и namespace. Каталог пакета `org.pixelrush.moneyiq` не переименовывай: это отдельный шаг с миграцией.

## Фаза B. Тесты: покрыть ВСЕ экраны и весь функционал (главное требование Сержа)
Сначала составь `.context/coverage-matrix-2673.md`: таблица «каждый экран / ViewModel / Repository / DAO / Worker / util / экспорт-импорт → тестовый файл → какие сценарии покрыты». Затем закрой каждую строку. Ни одна строка не должна остаться «нет теста» без причины.
- Чистая логика и ViewModel: JUnit + mockk + turbine + kotlinx-coroutines-test (StandardTestDispatcher, Dispatchers.setMain).
- DAO и миграции Room: Robolectric + in-memory Room. Проверь каждую миграцию схемы, если они есть.
- Экраны Compose: Robolectric + `createComposeRule` (compose-ui-test-junit4 + ui-test-manifest уже в libs.versions.toml; подключи в testImplementation/debugImplementation). На каждый экран минимум:
  - рендер с данными;
  - пустое состояние;
  - основной пользовательский сценарий (добавить, изменить и удалить операцию, счёт, категорию, бюджет; переключить месяц; отчёты; поиск и фильтр; калькулятор ввода суммы; настройки; бэкап, восстановление и CSV);
  - навигация по пунктам меню: ни один пункт не должен быть мёртвым;
  - диалоги подтверждения.
- Worker'ы: `androidx.work:work-testing` (TestListenableWorkerBuilder), если библиотека есть в офлайн-кеше. Если её нет, тестируй логику, вынесенную из Worker'а.
- Регрессии: на каждый баг из Фазы C сначала падающий тест, потом исправление.
- Если библиотеки нет в офлайн-кеше Gradle, напиши, какой именно, и тестируй тем, что есть. Не обходи это молча.

## Фаза C. Оставшиеся баги (из `.context/review-2673.md` и дизайн-ревью)
Исправить с тестом на каждый:
1. Неатомарная запись бэкапа: пиши во временный файл, затем rename.
2. Восстановление стирает данные до проверки бэкапа: сначала валидация и парсинг, потом транзакция с заменой.
3. Итоги по нескольким валютам суммируются без конвертации. Минимально: группировать итоги по валюте, не складывать разные валюты.
4. NaN и переполнение в калькуляторе ввода суммы.
5. WEEKLY-период в бюджете.
6. Дубли повторяющихся операций.
7. Сброс повторения при редактировании операции.
8. Формат валюты: символ, разделители, локаль uk.
9. Нет подтверждения у «Видалити бюджет».
10. Мёртвые пункты меню: подключить или убрать.
11. Состояние форм теряется при повороте (rememberSaveable).
12. Контраст токенов: закрыт палитрой из Фазы A.

Double для денег: оцени, сколько работы займёт перевод на Long в копейках или BigDecimal. Не переделывай, только опиши план в отчёте.

## Готово, когда
- Фазы A, B и C закоммичены.
- `bash ./gradlew --offline :app:testDebugUnitTest :app:assembleDebug` проходит. В отчёт приложи итог: число тестов, упавшие, BUILD SUCCESSFUL/FAILED.
- В отчёте:
  - список коммитов (sha и одна строка);
  - таблица coverage-matrix (сколько строк закрыто, что осталось и почему);
  - список багов: исправлен или нет, файл:строка;
  - честное перечисление того, что не удалось запустить.
