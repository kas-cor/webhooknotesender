# WebhookNoteSender — Setup Guide 🚀

## Настройка CI/CD и GitHub Secrets

### 1. Создание keystore для подписи release APK

Если у вас ещё нет keystore для подписи релизных сборок, создайте его:

```bash
# Создаём keystore (запомните введённые пароли!)
keytool -genkey -v -keystore webhooknotesender-release.jks \
        -alias webhooknotesender \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storetype PKCS12
```

> **Важно:** Никогда не коммитьте `.jks`, `.keystore` или файлы с паролями в git.  
> Файл `.gitignore` уже настроен на исключение `*.jks` и `*.keystore`.

### 2. Кодирование keystore в Base64 для GitHub Secrets

```bash
# Кодируем keystore в Base64
base64 -w0 webhooknotesender-release.jks > webhooknotesender-release.jks.base64

# Копируем содержимое
cat webhooknotesender-release.jks.base64 | wl-copy   # Linux (Wayland)
# или:
cat webhooknotesender-release.jks.base64 | xclip     # Linux (X11)
# или просто откройте файл и скопируйте вручную
```

> **После копирования удалите base64-файл:**
> ```bash
> rm webhooknotesender-release.jks.base64
> ```

### 3. Настройка GitHub Secrets

Перейдите в репозиторий на GitHub:
`https://github.com/kas-cor/webhooknotesender/settings/secrets/actions`

Нажмите **«New repository secret»** и добавьте следующие секреты:

| Secret | Значение |
|---|---|
| `KEYSTORE_BASE64` | Содержимое файла `webhooknotesender-release.jks.base64` (полная base64-строка) |
| `KEYSTORE_PASSWORD` | Пароль, который вы задали при создании keystore |
| `KEY_ALIAS` | Псевдоним ключа (по умолчанию: `webhooknotesender`) |
| `KEY_PASSWORD` | Пароль ключа (если не задан, используется `KEYSTORE_PASSWORD`) |

![GitHub Secrets](https://docs.github.com/assets/cb-25923/images/help/settings/actions-secrets-setting.png)

### 4. Проверка CI/CD Pipeline

После настройки секретов, CI/CD будет работать автоматически:

1. **Push в `main` или `develop`** — запускает:
   - `lint` — проверка кода (неблокирующая)
   - `test` — запуск unit-тестов (19 тестов)
   - `locales` — проверка соответствия строк EN и RU
   - `build-debug` — сборка debug APK

2. **Push тега `v*`** (например, `v1.0`) — дополнительно:
   - `build-release` — подписанная release сборка
   - `release` — создание GitHub Release с APK

### 5. Как сделать релиз

```bash
# 1. Убедитесь, что все изменения закоммичены
git status

# 2. Создайте тег новой версии
git tag v1.0

# 3. Запушьте тег
git push origin v1.0

# 4. CI/CD автоматически:
#    - Увеличит versionCode
#    - Соберёт подписанный APK
#    - Создаст GitHub Release с changelog и APK
```

---

## Локальный запуск CI-проверок

Перед пушем рекомендуется запустить те же проверки, что и в CI:

```bash
# Unit-тесты
./gradlew testDebugUnitTest

# Линтер
./gradlew lintDebug

# Сборка debug APK
./build.sh

# Сборка release APK (с подписью)
export KEYSTORE_PASSWORD=your-password
./build.sh --release
```

---

## Быстрый старт для нового разработчика

```bash
# Клонирование
git clone git@github.com:kas-cor/webhooknotesender.git
cd webhooknotesender

# Настройка Android SDK
echo "sdk.dir=/home/your-user/Android/Sdk" > local.properties

# Сборка
chmod +x gradlew build.sh
./build.sh

# Запуск тестов
./gradlew testDebugUnitTest
```

---

## Troubleshooting

### CI падает с ошибкой «No key store»

Убедитесь, что:
1. Создан keystore (`webhooknotesender-release.jks`)
2. Secrets `KEYSTORE_BASE64` и `KEYSTORE_PASSWORD` установлены в GitHub
3. Base64-строка скопирована целиком (без лишних пробелов и переносов)

### CI падает на этапе `test`

```bash
# Запустите тесты локально
./gradlew testDebugUnitTest

# Убедитесь, что все тесты проходят
# Если нет — исправьте ошибки и запушьте снова
```

### Release APK не подписывается

В `build.sh` реализован fallback: если keystore не найден или `KEYSTORE_PASSWORD` не задан, APK подписывается debug-ключом Android SDK.  
Для production-подписи всегда указывайте `KEYSTORE_PASSWORD`.

---

## Структура CI/CD файлов

```
.github/
├── dependabot.yml             # Автоматическое обновление зависимостей
└── workflows/
    └── build-apk.yml           # Основной CI/CD пайплайн

build.sh                        # Локальный скрипт сборки
SETUP.md                        # Эта инструкция
.gitignore                      # Игнорирование секретов и артефактов
```

---

## Ссылки

- [WebhookNoteSender на GitHub](https://github.com/kas-cor/webhooknotesender)
- [Workflow: Build APK](.github/workflows/build-apk.yml)
- [README.md](README.md) — English documentation
- [README_ru.md](README_ru.md) — Русская документация
- [AGENTS.md](AGENTS.md) — AI agent documentation
