# Техническое задание (ТЗ)

## Android-приложение «Webhook Sender» для отправки медиа на webhook ИИ-агента

---

## 1. Назначение и общее описание

Android-приложение на Kotlin, которое позволяет пользователю создавать именованные настройки (профили) для отправки изображений, аудио или видео на webhook-эндпоинт ИИ-агента. Приложение захватывает медиа через камеру или микрофон, кодирует файл в Base64, формирует JSON-сообщение, ставит его в очередь отправки и немедленно пытается отправить. При отсутствии интернета сообщение остаётся в очереди и ретраится до успешной отправки. Оригинальный файл удаляется сразу после кодирования — на устройстве хранятся только Base64-строки в очереди.

## 2. Целевая платформа

| Параметр | Значение |
|---|---|
| Язык | Kotlin |
| Минимальная версия Android | API 26 (Android 8.0) |
| Target SDK | API 35 (Android 15) |
| UI-фреймворк | Jetpack Compose + Material 3 |
| Архитектура | MVVM + Clean Architecture |
| Сборка | Gradle (Kotlin DSL), version catalog |

## 3. Функциональные требования

### 3.1. Управление настройками (профилями)

- Пользователь может создавать **одну или более** настроек (профилей).
- Каждый профиль содержит:
  - **Наименование** — текстовое поле, обязательное, уникальное в рамках приложения.
  - **Type** — выбор из трёх вариантов: `image`, `audio`, `video`. Определяет, какой захватчик вызывать (камера для image/video, микрофон для audio).
  - **Prompt** — многострочное текстовое поле, обязательное. Текст промпта для ИИ-агента.
  - **URL webhook** — текстовое поле, обязательное, валидация на корректный URL (схема `https://` или `http://`).
  - **Bearer token** — текстовое поле, опциональное. Используется в заголовке `Authorization: Bearer <token>`.
- Поддержка CRUD: создание, просмотр, редактирование, удаление профиля. Удаление профиля не удаляет сообщения из очереди, уже созданные для этого профиля.
- Список профилей отображается на главном экране в виде карточек. На каждой карточке отображается наименование, тип и кнопка быстрого действия («Снять/Записать»).
- Профили хранятся локально в Room database.

### 3.2. Захват медиа

#### 3.2.1. Изображение (type = image)
- Вызывается системная камера (через `ActivityResultContracts.TakePicture` или CameraX для более глубокого контроля — на усмотрение разработчика, CameraX предпочтительна для единообразия).
- После снимка файл временно сохраняется в cache-dir, немедленно кодируется в Base64, добавляется в очередь, временный файл удаляется.

#### 3.2.2. Видео (type = video)
- Вызывается системная камера в режиме видео (`ActivityResultContracts.CaptureVideo` или CameraX).
- После записи файл временно сохраняется в cache-dir, кодируется в Base64, добавляется в очередь, временный файл удаляется.

#### 3.2.3. Аудио (type = audio)
- Запись аудио через `MediaRecorder` (формат AAC, выходной файл во cache-dir).
- UI записи: кнопка «Начать запись» / «Остановить запись» с таймером длительности.
- После остановки записи файл кодируется в Base64, добавляется в очередь, временный файл удаляется.

### 3.3. Кодирование и формат данных

#### 3.3.1. Кодирование файла в Base64
- Файл читается потоково (не загружается целиком в память) и кодируется в Base64.
- Рекомендуется чанковое чтение + `Base64.encodeToString()` для каждого чанка, либо `android.util.Base64` с флагом `NO_WRAP`.
- Для больших видеофайлов — предусмотреть предупреждение пользователю о размере, но не блокировать отправку.

#### 3.3.2. Формат JSON-сообщения

Тело POST-запроса на webhook:

```json
{
  "messages": [
    {
      "name": "string",
      "prompt": "string",
      "datetime": "string",
      "type": "string",
      "data": "string"
    }
  ]
}
```

Описание полей:
- `name` — наименование профиля (из настройки).
- `prompt` — промпт (из настройки).
- `datetime` — дата и время захвата медиа в формате ISO 8601 (UTC): `yyyy-MM-dd'T'HH:mm:ss'Z'`.
- `type` — тип медиа: `image`, `audio` или `video`.
- `data` — содержимое файла, закодированное в Base64 (строка без переносов строк).

#### 3.3.3. Заголовки запроса
- `Content-Type: application/json`
- `Authorization: Bearer <token>` — если в профиле указан bearer token.
- `Accept: application/json`

### 3.4. Очередь отправки

#### 3.4.1. Структура очереди
- Очередь хранится в Room database (таблица `queue_items`).
- Каждая запись в очереди содержит:
  - `id` — автоинкрементный первичный ключ (Long).
  - `profile_name` — имя профиля (для отображения).
  - `url` — webhook URL.
  - `bearer_token` — токен (может быть null).
  - `json_payload` — готовый JSON-串 (сериализованный объект запроса, включая Base64-строку).
  - `status` — enum: `PENDING`, `SENDING`, `SENT`, `FAILED`.
  - `created_at` — timestamp создания.
  - `attempts` — счётчик попыток отправки (int, default 0).
  - `last_error` — текст последней ошибки (может быть null).

#### 3.4.2. Логика обработки очереди
- При добавлении нового элемента в очередь — немедленно запускается попытка отправки (если воркер не уже активен).
- Используется `WorkManager` с `CoroutineWorker` для фоновой обработки.
- Стратегия:
  - Если интернет доступен — отправка выполняется.
  - Если интернет отсутствует или запрос не удался — элемент остаётся в статусе `PENDING`, воркер ставит WorkManager `Constraints` на наличие сети и ретраится автоматически при появлении интернета.
  - Используется `ExponentialBackoff` для повторных попыток: начальная задержка 30 секунд, максимум 5 минут, без ограничения количества попыток — сообщение отправляется пока не уйдёт.
  - При успешной отправке — статус меняется на `SENT`, запись удаляется из очереди через 24 часа (или немедленно — на усмотрение разработчика, предпочтительно немедленное удаление после подтверждения успеха).
- Слушатель изменения состояния сети (`ConnectivityManager.NetworkCallback`) для немедленного запуска воркера при появлении интернета.

#### 3.4.3. Просмотр очереди
- Отдельный экран «Очередь» (доступен из главного экрана через bottom navigation или пункт меню).
- Отображается список записей очереди с информацией: имя профиля, тип медиа, дата создания, статус, количество попыток, последняя ошибка.
- Возможность удалить запись из очереди вручную (свайп или кнопка удаления).
- Возможность принудительно повторить отправку выбранной записи (ручной retry).
- Индикатор состояния: общее количество ожидающих сообщений (badge на иконке очереди).

### 3.5. HTTP-клиент
- Используется OkHttp (или Ktor Client — на усмотрение разработчика, OkHttp предпочтителен).
- Таймауты: connect = 30s, read = 120s, write = 120s (Base64-строки могут быть большими).
- Обработка ошибок: HTTP-коды 4xx (кроме 408, 429) — пометить как `FAILED` (сервер принял запрос, но вернул ошибку — повтор нет смысла), HTTP 5xx / таймаут / отсутствие сети — остаться в `PENDING` для ретрая.

### 3.6. Ярлыки на главном экране устройства (Dynamic Shortcuts)

#### 3.6.1. Общее описание
- Пользователь может создать ярлык (shortcut) на главном экране устройства для любого профиля.
- Ярлык привязан к конкретному профилю и содержит его ID.
- При нажатии на ярлык:
  - **image** — немедленно открывается камера (системная или CameraX), без открытия главного экрана приложения.
  - **video** — немедленно открывается камера в режиме видео, без открытия главного экрана приложения.
  - **audio** — немедленно запускается запись аудио (foreground service с уведомлением), без открытия главного экрана приложения.
- После захвата/записи:
  - Файл кодируется в Base64, удаляется, JSON добавляется в очередь.
  - Пользователю показывается короткое toast-уведомление или notification: «Добавлено в очередь» / «Отправлено».
  - Приложение **не открывается** — весь процесс происходит без видимого UI приложения (transparent activity или service).
- Очередь обрабатывается как обычно — немедленная отправка при наличии интернета, иначе ретрай.

#### 3.6.2. Реализация
- Используется `ShortcutManager` (API 25+) / `ShortcutInfoCompat` из `androidx.core:core-ktx`.
- Создание ярлыка через `ShortcutManagerCompat.requestPinShortcut()` (API 26+) — добавляет ярлык на домашний экран с подтверждением пользователя.
- Ярлык содержит:
  - `id` — уникальный, формат: `shortcut_<profileId>`.
  - `shortLabel` — наименование профиля.
  - `longLabel` — наименование профиля + тип (например: «Аудио заметка · audio»).
  - `icon` — иконка в зависимости от типа: камера для image/video, микрофон для audio.
  - `intent` — `Intent` с action `ACTION_CAPTURE_SHORTCUT`, extra `profile_id` (Long), направленный в `ShortcutReceiverActivity` (transparent activity).

#### 3.6.3. ShortcutReceiverActivity (transparent activity)
- Activity без UI (тема `Theme.NoDisplay` или прозрачная), которая:
  1. Читает `profile_id` из Intent.
  2. Загружает профиль из БД.
  3. Для **image/video** — запускает `ActivityResultContracts.TakePicture` / `CaptureVideo` (или CameraX intent).
  4. Для **audio** — запускает `AudioRecorderService` (foreground service).
  5. После получения результата — кодирует в Base64, удаляет файл, добавляет в очередь, показывает toast/notification, завершается (`finish()`).
- Activity не отображается пользователю — используется тема `android:theme="@style/Theme.Transparent"`.

#### 3.6.4. Управление ярлыками
- В UI профиля (экран редактирования) — кнопка «Создать ярлык на главном экране».
- В UI профиля — кнопка «Удалить ярлык» (если ярлык уже создан).
- При удалении профиля — связанные ярлыки удаляются автоматически (`ShortcutManager.removeDynamicShortcuts()` или `removeShortcutCompat()`).
- При обновлении профиля (изменение имени) — ярлык обновляется (`ShortcutManager.updateShortcuts()`).
- Статус наличия ярлыка отображается в карточке профиля (иконка ярлыка).

#### 3.6.5. Pinned shortcuts vs Dynamic shortcuts
- Используются **pinned shortcuts** (через `requestPinShortcut`) — ярлык закрепляется на домашнем экране и остаётся даже после удаления приложения (до API 26 — стандартное поведение). Начиная с API 26, pinned shortcuts управляются системой.
- Dynamic shortcuts также добавляются параллельно — они отображаются в долгом нажатии на иконку приложения (long-press menu). Это позволяет быстро запускать захват из меню иконки приложения без создания ярлыка на рабочем столе.
- Рекомендуется обновлять dynamic shortcuts для всех профилей при каждом изменении списка профилей (максимум 5 dynamic shortcuts — показать первые 5 или наиболее используемые).

## 4. UI/UX требования

### 4.1. Навигация
- Navigation Compose с bottom navigation bar:
  - **Профили** — список настроек, создание/редактирование.
  - **Очередь** — список очереди отправки.
  - **Настройки** — тема приложения, язык (опционально).

### 4.2. Главный экран (Профили)
- Список карточек профилей.
- Каждая карточка: наименование, иконка типа (image/audio/video), краткая информация.
- Кнопка действия на карточке: иконка камеры (для image/video) или микрофона (для audio).
- FAB «+» для создания нового профиля.
- Long-press на карточке — меню: редактировать, удалить.

### 4.3. Экран создания/редактирования профиля
- Форма с полями: Наименование, Type (dropdown), Prompt (multiline), URL webhook, Bearer token.
- Валидация в реальном времени.
- Кнопка «Тестовая отправка» (опционально, но желательно) — отправляет тестовый JSON с пустой Base64-строкой для проверки связности.
- Кнопка сохранения.

### 4.4. Экран записи аудио (type = audio)
- Кнопка «Начать запись» (большая, круглая, красная).
- Таймер длительности записи.
- Кнопка «Остановить» → кодирование → добавление в очередь → возврат на главный экран с snackbar-уведомлением.

### 4.5. Экран очереди
- Список элементов с цветовой индикацией статуса:
  - ⏳ PENDING — жёлтый/оранжевый.
  - 🔄 SENDING — синий.
  - ✅ SENT — зелёный (временно, затем удаляется).
  - ❌ FAILED — красный.
- Свайп влево — удалить запись.
- Кнопка «Повторить» на каждой записи со статусом FAILED или PENDING.
- Pull-to-refresh для обновления списка.

### 4.6. Уведомления
- Уведомление (Notification) при успешной отправке (опционально, по настройке).
- Уведомление при ошибке отправки после N попыток.
- Persistent notification во время записи аудио (foreground service).

## 5. Тема оформления

- Поддержка трёх режимов темы:
  - **Светлая** (Light).
  - **Тёмная** (Dark).
  - **Системная** (Auto — следует системной настройке).
- Реализация через Material 3 dynamic color scheme (где доступно) + статические палитры для fallback.
- Выбор темы хранится в DataStore Preferences.
- Цветовая схема: основная — Material 3 default, можно кастомизировать primary/secondary цвета.

## 6. Локализация

### 6.1. Структура
- Основной язык: **Английский** (`values/strings.xml`).
- Второй язык: **Русский** (`values-ru/strings.xml`).
- Все пользовательские строки вынесены в string resources. Никаких хардкод-строк в коде.
- Структура `strings.xml` — с указанием `translatable="false"` для технических строк.

### 6.2. Расширяемость
- Добавление новой локализации = добавление папки `values-<locale>/strings.xml` с переводами.
- В настройках приложения — возможность выбора языка вручную (список доступных языков определяется по наличию `values-*` директорий или явным списком).
- Переключение языка применяется без перезапуска приложения (через `AppCompatDelegate.setApplicationLocales()` / `LocaleManager`).

### 6.3. Минимальный набор строк для перевода
- Названия экранов, кнопок, полей форм.
- Сообщения об ошибках валидации.
- Статусы очереди.
- Уведомления.

## 7. Технический стек

| Компонент | Технология |
|---|---|
| Язык | Kotlin 2.0+ |
| UI | Jetpack Compose + Material 3 |
| Навигация | Navigation Compose |
| База данных | Room |
| Preferences | DataStore Preferences |
| HTTP-клиент | OkHttp 4.x |
| Фоновые задачи | WorkManager + CoroutineWorker |
| Камера | CameraX (предпочтительно) или ActivityResultContracts |
| Аудио запись | MediaRecorder |
| DI | Hilt (Dagger Hilt) |
| Сериализация JSON | kotlinx.serialization или Moshi |
| Корутины | Kotlin Coroutines + Flow |
| Локализация | Android resource qualifiers + LocaleManager |
| Тестирование | JUnit4, MockK, Turbine, Compose Testing |

## 8. Структура проекта

```
app/src/main/java/com/example/webhooksender/
├── WebhookSenderApp.kt              // Application class, Hilt entry point
├── MainActivity.kt                   // Single Activity, Compose host
├── ShortcutReceiverActivity.kt       // Transparent activity для запуска из ярлыков
├── di/
│   ├── AppModule.kt                  // DataStore, OkHttp, JSON
│   ├── DatabaseModule.kt             // Room database, DAOs
│   └── RepositoryModule.kt           // Repository bindings
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt            // Room database
│   │   ├── dao/
│   │   │   ├── ProfileDao.kt
│   │   │   └── QueueDao.kt
│   │   └── entity/
│   │       ├── ProfileEntity.kt
│   │       └── QueueItemEntity.kt
│   ├── repository/
│   │   ├── ProfileRepository.kt
│   │   └── QueueRepository.kt
│   ├── remote/
│   │   └── WebhookApi.kt             // OkHttp-based sender
│   └── model/
│       ├── Profile.kt
│       ├── QueueItem.kt
│       └── WebhookPayload.kt         // Сериализуемая модель JSON
├── domain/
│   ├── usecase/
│   │   ├── CreateProfileUseCase.kt
│   │   ├── UpdateProfileUseCase.kt
│   │   ├── DeleteProfileUseCase.kt
│   │   ├── CaptureMediaUseCase.kt
│   │   ├── EnqueueMessageUseCase.kt
│   │   ├── ProcessQueueUseCase.kt
│   │   └── CreateShortcutUseCase.kt   // Создание/удаление/обновление ярлыков
│   └── model/
│       ├── MediaType.kt              // enum: IMAGE, AUDIO, VIDEO
│       ├── QueueStatus.kt            // enum: PENDING, SENDING, SENT, FAILED
│       └── ThemeMode.kt              // enum: LIGHT, DARK, SYSTEM
├── work/
│   └── QueueWorker.kt                // CoroutineWorker для отправки очереди
├── ui/
│   ├── theme/
│   │   ├── Theme.kt                  // Material 3 theme, dynamic color
│   │   ├── Color.kt
│   │   └── Type.kt
│   ├── navigation/
│   │   └── AppNavigation.kt          // NavHost, bottom nav
│   ├── profiles/
│   │   ├── ProfilesScreen.kt
│   │   ├── ProfilesViewModel.kt
│   │   └── ProfileEditScreen.kt
│   ├── queue/
│   │   ├── QueueScreen.kt
│   │   └── QueueViewModel.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   └── components/
│       ├── CaptureButton.kt
│       ├── AudioRecorder.kt          // Composable + логика записи
│       └── StatusBadge.kt
└── util/
    ├── Base64Encoder.kt              // Потоковое кодирование файла
    ├── NetworkMonitor.kt             // ConnectivityManager callback
    ├── DateTimeUtils.kt
    └── ShortcutHelper.kt             // Создание/удаление/обновление pinned и dynamic shortcuts
```

## 9. Схема базы данных (Room)

### Таблица `profiles`
| Колонка | Тип | Описание |
|---|---|---|
| id | Long (PK, autoGenerate) | Идентификатор |
| name | String | Наименование, UNIQUE |
| type | String | `image`, `audio`, `video` |
| prompt | String | Промпт |
| url | String | Webhook URL |
| bearer_token | String? | Bearer token, nullable |

### Таблица `queue_items`
| Колонна | Тип | Описание |
|---|---|---|
| id | Long (PK, autoGenerate) | Идентификатор |
| profile_name | String | Имя профиля (снапшот на момент создания) |
| url | String | Webhook URL (снапшот) |
| bearer_token | String? | Token (снапшот) |
| json_payload | String | Готовый JSON с Base64-данными |
| media_type | String | `image`, `audio`, `video` |
| status | String | `PENDING`, `SENDING`, `SENT`, `FAILED` |
| attempts | Int | Количество попыток (default 0) |
| last_error | String? | Текст последней ошибки |
| created_at | Long | Unix timestamp (мс) |

## 10. Безопасность

- Bearer token хранится в Room (рассмотреть EncryptedSharedPreferences для дополнительной защиты — на усмотрение разработчика, желательно).
- Webhook URL отправляется только по HTTPS (предупреждение при HTTP).
- Файлы не хранятся на устройстве: после кодирования в Base64 временный файл немедленно удаляется.
- Разрешения (permissions) запрашиваются в runtime: `CAMERA`, `RECORD_AUDIO`, `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (API 33+).
- Foreground service для аудиозаписи: тип `microphone`.

## 11. Обработка краевых случаев

- **Большие видеофайлы**: предупреждение пользователю о потенциально большом объёме Base64. Рекомендация ограничить длительность видео (настраиваемый лимит, по умолчанию 60 секунд).
- **Нет интернета при захвате**: медиа кодируется и добавляется в очередь, отправка откладывается до появления сети.
- **Приложение закрыто во время записи аудио**: foreground service предотвращает убийство процесса.
- **Приложение убито во время отправки**: WorkManager гарантирует повторный запуск воркера.
- **Webhook возвращает ошибку**: HTTP 4xx (кроме 408/429) → FAILED, HTTP 5xx / network error → PENDING + retry.
- **Дублирование профилей**: проверка уникальности имени при создании/редактировании.
- **Поворот экрана**: состояние UI сохраняется через ViewModel + `rememberSaveable`.
- **Ярлык удалённого профиля**: если пользователь нажал ярлык, а профиль уже удалён — показывается toast «Профиль не найден» и ярлык удаляется.
- **Разрешения не даны при запуске из ярлыка**: `ShortcutReceiverActivity` запрашивает нужные permission (camera / record_audio) в runtime. Если пользователь отказывает — toast «Нет разрешений» и завершение.
- **Одновременный запуск из ярлыка и из приложения**: `ShortcutReceiverActivity` использует `android:launchMode="singleTop"` для предотвращения дублирования.
- **Динамические ярлыки превышают лимит**: система ограничивает 5 dynamic shortcuts — показывать первые 5 или наиболее используемые профили.

## 12. Манифест

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.microphone" android:required="false" />
```

Foreground service для аудиозаписи:
```xml
<service
    android:name=".ui.components.AudioRecorderService"
    android:foregroundServiceType="microphone"
    android:exported="false" />
```

Transparent activity для запуска из ярлыков:
```xml
<activity
    android:name=".ShortcutReceiverActivity"
    android:theme="@style/Theme.Transparent"
    android:exported="true"
    android:noHistory="true"
    android:excludeFromRecents="true">
    <intent-filter>
        <action android:name="com.example.webhooksender.CAPTURE_SHORTCUT" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

Тема для transparent activity (в `themes.xml`):
```xml
<style name="Theme.Transparent" parent="Theme.Material3.NoActionBar">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:windowContentOverlay">@null</item>
    <item name="android:windowNoTitle">true</item>
    <item name="android:windowAnimationStyle">@null</item>
</style>
```

## 13. Критерии приёмки

1. ✅ Пользователь может создать, отредактировать и удалить профиль с полями: наименование, type, prompt, URL, bearer token.
2. ✅ При нажатии кнопки на профиле image/video — открывается камера, снимок/видео кодируется в Base64, файл удаляется, JSON добавляется в очередь.
3. ✅ При нажатии кнопки на профиле audio — открывается рекордер, запись кодируется в Base64, файл удаляется, JSON добавляется в очередь.
4. ✅ JSON-формат соответствует спецификации из раздела 3.3.2.
5. ✅ Очередь обрабатывается автоматически: при наличии интернета — немедленная отправка, при отсутствии — ретрай при появлении сети.
6. ✅ Очередь доступна для просмотра в приложении: статусы, количество попыток, ошибки.
7. ✅ Возможность ручного удаления и повторной отправки элементов очереди.
8. ✅ Поддержка тёмной, светлой и системной темы.
9. ✅ Английский и русский языки, все строки в resources.
10. ✅ Добавление нового языка = добавление `values-<locale>/strings.xml`.
11. ✅ Файлы не сохраняются на устройстве после кодирования.
12. ✅ Приложение собирается и запускается на API 26–35.
13. ✅ Пользователь может создать ярлык на главном экране для любого профиля.
14. ✅ При нажатии на ярлык image/video — камера запускается без видимого открытия приложения, после снимка — файл кодируется, удаляется, JSON встаёт в очередь.
15. ✅ При нажатии на ярлык audio — запись аудио запускается без видимого открытия приложения (foreground service), после остановки — файл кодируется, удаляется, JSON встаёт в очередь.
16. ✅ При удалении профиля связанные ярлыки удаляются автоматически.
17. ✅ Dynamic shortcuts отображаются в long-press меню иконки приложения.

## 14. Ограничения и примечания

- Размер Base64-строки для видео может быть значительным (видео 10 сек → ~5–15 МБ Base64). Webhook-сервер должен быть готов принимать большие payload'ы. Рекомендуется ограничить длительность видео (по умолчанию 60 сек, настраивается).
- Приложение не реализует пагинацию или серверную синхронизацию — только fire-and-forget с локальной очередью.
- push-уведомления от webhook-сервера не предусмотрены (ответ сервера игнорируется после подтверждения HTTP 200).
- Приложение работает только в портретной ориентации (или адаптивно — на усмотрение разработчика).

---

*ТЗ подготовлено для ИИ-агента-разработчика. Документ содержит все необходимые требования для реализации MVP.*