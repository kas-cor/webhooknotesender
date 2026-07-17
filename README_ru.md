# WebhookNoteSender 🚀

[![CI](https://github.com/kas-cor/webhooknotesender/actions/workflows/build-apk.yml/badge.svg)](https://github.com/kas-cor/webhooknotesender/actions/workflows/build-apk.yml)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-purple)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-BOM%202024.12.01-green)](https://developer.android.com/jetpack/compose)
[![Material3](https://img.shields.io/badge/Material%203-dynamic-blue)](https://m3.material.io/)
[![API](https://img.shields.io/badge/minSdk-26%20%7C%20target-35-orange)](app/build.gradle.kts)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![English](https://img.shields.io/badge/README-English-blue)](README.md)

> Android-приложение для захвата медиа (фото, видео, аудио) и отправки на webhook ИИ-агента.  
> **Два языка**: английский и русский.

---

## Возможности ✨

- **Управление профилями** — создание, редактирование, удаление именованных профилей с URL webhook'а, промптом, bearer-токеном и типом медиа
- **Захват медиа** — фото/видео через системную камеру, аудио через MediaRecorder
- **Кодирование в Base64** — потоковое кодирование, временный файл удаляется сразу после кодирования
- **Система очередей** — очередь в Room database с фоновой обработкой через WorkManager
- **Автоматический повтор** — экспоненциальная задержка (30с → макс. 5мин), повтор при появлении сети
- **Умная обработка ошибок** — HTTP 4xx (кроме 408/429) → `FAILED`, HTTP 5xx / сетевая ошибка → `PENDING` + повтор
- **Ярлыки на главном экране** — быстрый захват в одно касание без открытия приложения
- **Динамические ярлыки** — меню при долгом нажатии на иконку приложения
- **Запись аудио в фоне** — постоянное уведомление с кнопкой остановки
- **Тёмная / Светлая / Системная тема** — Material 3 dynamic color с ручным переключением
- **Двуязычный интерфейс** — английский и русский, все строки в ресурсах
- **JSON payload** — корректный формат `{"messages": [...]}` согласно спецификации

---

## Архитектура 🏗️

```
WebhookNoteSenderApp (Hilt Application)
└─ MainActivity (ComponentActivity + Compose)
   └─ AppNavigation (Navigation Compose, Bottom Nav)
      ├─ ProfilesScreen        — список профилей в виде карточек
      ├─ ProfileEditScreen     — форма создания/редактирования профиля
      ├─ QueueScreen           — очередь с индикацией статуса
      └─ SettingsScreen        — выбор темы и языка
   └─ ShortcutReceiverActivity — прозрачная activity для ярлыков
   └─ AudioRecorderService     — foreground service для записи аудио
```

### Слои чистой архитектуры

```
┌──────────────────────────────────────────────┐
│  UI слой (Compose + ViewModels)              │
│  ProfilesScreen · QueueScreen · Settings     │
├──────────────────────────────────────────────┤
│  Domain слой                                 │
│  MediaType · QueueStatus · ThemeMode         │
├──────────────────────────────────────────────┤
│  Data слой                                   │
│  Room БД · Репозитории · WebhookApi (OkHttp) │
├──────────────────────────────────────────────┤
│  Инфраструктура                              │
│  WorkManager · Hilt DI · DataStore · CameraX │
└──────────────────────────────────────────────┘
```

### Структура проекта

```
webhooknotesender/
├── app/src/main/java/com/kascorp/webhooknotesender/
│   ├── WebhookNoteSenderApp.kt              # Hilt Application
│   ├── MainActivity.kt                       # Единственная Activity
│   ├── ShortcutReceiverActivity.kt           # Прозрачная activity для ярлыков
│   ├── di/                                   # Hilt модули
│   ├── data/                                 # Room, репозитории, WebhookApi
│   ├── work/                                 # QueueWorker (фоновая отправка)
│   ├── ui/                                   # Тема, навигация, экраны, компоненты
│   └── util/                                 # Base64Encoder, NetworkMonitor, ShortcutHelper
├── app/src/main/res/
│   ├── values/strings.xml                    # Английский (72 строки)
│   └── values-ru/strings.xml                 # Русский (72 строки)
├── build.sh                                  # Универсальный скрипт сборки
├── .github/workflows/build-apk.yml           # CI/CD конвейер
├── AGENTS.md                                 # Документация для AI-агентов
└── README.md                                 # Английская версия README
```

---

## Технологический стек ⚡

| Компонент | Технология |
|---|---|
| **Язык** | Kotlin 2.1.0 |
| **UI** | Jetpack Compose + Material 3 (BOM 2024.12) |
| **Навигация** | Navigation Compose (Bottom Nav) |
| **DI** | Dagger Hilt 2.53 |
| **База данных** | Room 2.6.1 (KSP) |
| **HTTP** | OkHttp 4.12 |
| **Фон** | WorkManager + CoroutineWorker |
| **Сериализация** | kotlinx.serialization 1.7.3 |
| **Настройки** | DataStore Preferences |
| **Камера** | CameraX 1.4.1 + ActivityResultContracts |
| **Аудио** | MediaRecorder + Foreground Service |
| **minSdk / targetSdk / compileSdk** | 26 / 35 / 36 |
| **Тестирование** | JUnit 4.13.2 |

---

## Быстрый старт 🚀

### Требования

- Android Studio Hedgehog (2023.1.1+) или IntelliJ IDEA
- JDK 17+
- Android SDK API 35

### Сборка и установка

```bash
# Сделать скрипты исполняемыми
chmod +x build.sh gradlew

# Сборка debug APK
./build.sh

# Сборка, установка и запуск на устройстве
./build.sh --run

# Установка APK на устройство
./build.sh --install

# Запуск приложения на устройстве
./build.sh --launch

# Очистка данных приложения
./build.sh --clear

# Показать логи приложения (logcat)
./build.sh --logs

# Отправить тестовый POST на webhook
./build.sh --test https://your-webhook.com/endpoint
./build.sh --test https://your-webhook.com/endpoint your-bearer-token
```

### Напрямую через Gradle

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release сборка

```bash
# Release с debug-ключом (без KEYSTORE_PASSWORD)
./build.sh --release

# Release с продакшн-ключом
export KEYSTORE_PASSWORD=your-password
./build.sh --release
```

---

## Build-скрипт (`build.sh`) 🔧

Универсальный bash-скрипт для сборки, отладки и тестирования:

| Флаг | Описание |
|---|---|
| *(без флагов)* | Собрать debug APK |
| `--run` | Собрать debug APK, установить и запустить |
| `--release` | Собрать release APK (подпись продакшн или debug ключом) |
| `--install [apk]` | Установить APK на устройство (по умолч. debug) |
| `--launch` | Запустить приложение на подключённом устройстве |
| `--clear` | Очистить данные приложения на устройстве |
| `--logs` | Показать logcat, отфильтрованный по пакету приложения |
| `--test <url> [token]` | Отправить тестовый POST на webhook |
| `--help` | Показать справку |

---

## Payload вебхука 📡

### Формат запроса

| Атрибут | Значение |
|---|---|
| **Метод** | `POST` |
| **Content-Type** | `application/json` |
| **Authorization** | `Bearer <token>` (опционально) |
| **Accept** | `application/json` |
| **Таймаут** | connect: 30с, read: 120с, write: 120с |

### JSON Payload

```json
{
  "messages": [
    {
      "name": "Имя_профиля",
      "prompt": "Опишите, что ИИ должен сделать с этим медиа...",
      "datetime": "2026-07-18T12:00:00Z",
      "type": "image",
      "data": "/9j/4AAQSkZJRg...Base64-закодированное-содержимое..."
    }
  ]
}
```

| Поле | Описание |
|---|---|
| `name` | Имя профиля (слепок на момент захвата) |
| `prompt` | Промпт ИИ из конфигурации профиля |
| `datetime` | Время захвата в ISO 8601 UTC (`yyyy-MM-dd'T'HH:mm:ss'Z'`) |
| `type` | Тип медиа: `image`, `audio` или `video` |
| `data` | Base64-закодированное содержимое файла (NO_WRAP) |

### Стратегия повторов очереди

| HTTP статус | Действие |
|---|---|
| 200, 201, 204 | ✅ Пометить как `SENT`, удалить из очереди |
| 408, 429 | 🔄 Оставить `PENDING`, повтор с экспоненциальной задержкой |
| 4xx (кроме 408, 429) | ❌ Пометить как `FAILED`, без повтора |
| 5xx | 🔄 Оставить `PENDING`, повторить |
| Сетевая ошибка / таймаут | 🔄 Оставить `PENDING`, повторить при появлении сети |

---

## Ярлыки на главном экране 📌

Каждый профиль можно закрепить на домашнем экране для захвата в один клик:

| Тип | Действие ярлыка |
|---|---|
| **Image** | Открывает камеру напрямую, без UI приложения |
| **Video** | Открывает видеокамеру напрямую, без UI приложения |
| **Audio** | Запускает foreground service записи, без UI приложения |

**Как создать:**
1. Долгое нажатие на карточку профиля → **Создать ярлык**
2. Система запросит подтверждение закрепления на домашнем экране

**Динамические ярлыки** (меню долгого нажатия на иконку):
- Первые 5 профилей доступны в меню долгого нажатия
- Обновляются автоматически при изменении профилей

---

## CI/CD 🚀

Workflow: [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)

### Триггеры

- **Push** в `main`, `develop`
- **Pull Request** в `main`
- **Push тега** `v*` (например, `v1.0`)
- **Вручную** через `workflow_dispatch`

### Конвейер

```
Push
 ├─ lint       — lintDebug (continue-on-error: true)
 ├─ test       — testDebugUnitTest → загрузка артефакта test-results
 ├─ locales    — проверка соответствия ключей EN и RU
 └─ build-debug — assembleDebug → загрузка debug APK (7 дней)

Push тега (после параллельных задач):
 └─ build-release
     ├─ увеличение versionName из тега, versionCode +1
     ├─ коммит + push в main
     ├─ расшифровка keystore из KEYSTORE_BASE64 secret
     ├─ assembleRelease (подписанный)
     └─ загрузка release APK (30 дней)

Push тега (после build-release):
 └─ release
     ├─ загрузка release APK
     ├─ генерация changelog из git log
     └─ создание GitHub Release с APK
```

### GitHub Secrets

| Secret | Описание |
|---|---|
| `KEYSTORE_BASE64` | `webhooknotesender-release.jks` в base64 |
| `KEYSTORE_PASSWORD` | Пароль keystore |

### Процесс релиза

```bash
git tag v1.0
git push origin v1.0
# CI: увеличить версию → собрать → создать GitHub Release
```

---

## Локализация 🌐

- **Английский** — `app/src/main/res/values/strings.xml` (72 строки)
- **Русский** — `app/src/main/res/values-ru/strings.xml` (72 строки)

### Как добавить новый язык

```bash
# 1. Создать директорию локали
mkdir -p app/src/main/res/values-de

# 2. Скопировать английские строки
cp app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml

# 3. Перевести все значения <string> (атрибуты name не трогать!)
# 4. Обновить SettingsScreen для включения новой локали

# 5. Добавить бейдж-ссылку в README_ru.md:
# [![Deutsch](https://img.shields.io/badge/README-Deutsch-blue)](README.de.md)
```

> **Совет:** Все форматные плейсхолдеры (`%1$s`, `%2$d`) должны совпадать между языками — они подставляются программно.

---

## Разрешения 🔐

| Разрешение | Назначение |
|---|---|
| `INTERNET` | HTTP-запросы к webhook |
| `ACCESS_NETWORK_STATE` | Мониторинг сети |
| `CAMERA` | Фото и видео съёмка |
| `RECORD_AUDIO` | Запись аудио |
| `POST_NOTIFICATIONS` | Уведомление foreground service (API 33+) |
| `FOREGROUND_SERVICE` | Сервис записи аудио |
| `FOREGROUND_SERVICE_MICROPHONE` | Тип foreground service для микрофона |

---

## Список изменений 📋

| Версия | Дата | Что нового |
|---|---|---|
| v1.0.0 | 2026-07-18 | Первый релиз: CRUD профилей, захват медиа, очередь с WorkManager, ярлыки, двуязычный UI |

---

## Лицензия 📄

MIT

---

[![English](https://img.shields.io/badge/README-English-blue)](README.md)
[![Русский](https://img.shields.io/badge/README-%D0%A0%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9-blue)](README_ru.md)
