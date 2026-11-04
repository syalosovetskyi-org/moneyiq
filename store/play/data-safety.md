# Google Play Data safety — Zerno

Play release использует Firebase Crashlytics (Google) для стабильности приложения.
Debug: автоматический сбор и recordException выключены.

| Тип данных | Собираются | Передаются третьим лицам | Защита | Цель |
|---|---|---|---|---|
| Crash logs | Да | Нет (Google — обработчик/поставщик сервиса) | Шифрование при передаче | Стабильность, диагностика сбоев |
| Diagnostics | Да | Нет (Google — обработчик/поставщик сервиса) | Шифрование при передаче | Стабильность, диагностика сбоев |

Analytics не подключён. Personal использует отдельный SDK и эту декларацию не определяет.

Документация: https://firebase.google.com/docs/android/play-data-disclosure
