# Деплой Expense Tracker Bot на Railway

## Что было изменено

| Файл | Что изменено |
|------|-------------|
| `pom.xml` | Исправлены несуществующие зависимости (`spring-boot-starter-data-jpa-test` и др.), версия Spring Boot понижена с `4.0.6` → `3.3.5` (стабильная) |
| `Dockerfile` | Добавлены JVM-флаги для контейнеров (`UseContainerSupport`, `MaxRAMPercentage`) |
| `application.properties` | Убран хардкод, все значения через env-переменные; добавлена `server.port=${PORT:8080}` |
| `src/.../config/DatabaseConfig.java` | **Новый файл** — конвертирует Railway `postgres://` → JDBC `jdbc:postgresql://` |
| `railway.toml` | **Новый файл** — конфигурация Railway |

---

## Шаг 1 — Подготовка репозитория

Скопируйте изменённые файлы в ваш проект и запушьте в GitHub/GitLab:

```bash
git add .
git commit -m "feat: prepare for Railway deployment"
git push
```

---

## Шаг 2 — Создание проекта в Railway

1. Зайдите на [railway.app](https://railway.app) и войдите через GitHub
2. Нажмите **New Project → Deploy from GitHub repo**
3. Выберите ваш репозиторий

---

## Шаг 3 — Добавить PostgreSQL

1. В проекте нажмите **+ New** → **Database** → **Add PostgreSQL**
2. Railway автоматически создаст базу и добавит переменную `DATABASE_URL`

---

## Шаг 4 — Переменные окружения

В настройках сервиса бота (**Variables**) добавьте:

| Переменная | Значение |
|------------|---------|
| `TELEGRAM_BOT_TOKEN` | Токен от @BotFather |
| `TELEGRAM_BOT_USERNAME` | Username бота (без `@`) |

> `DATABASE_URL` и `PORT` Railway добавит автоматически — вручную не нужно.

---

## Шаг 5 — Деплой

Railway автоматически начнёт сборку после пуша. Вы можете следить за логами в разделе **Deployments**.

Порядок при первом старте:
1. Docker build (~3–5 мин из-за скачивания зависимостей Maven)
2. Liquibase автоматически применит миграции (`001-init.xml`)
3. Бот подключится к Telegram

---

## Локальная разработка после изменений

`.env` файл (уже в `.gitignore`):

```env
DATABASE_URL=jdbc:postgresql://localhost:5432/expense_tracker
TELEGRAM_BOT_TOKEN=ваш_токен
TELEGRAM_BOT_USERNAME=ваш_бот
PORT=8080
```

Запуск локально:
```bash
# Поднять только PostgreSQL
docker-compose up postgres -d

# Запустить приложение
./mvnw spring-boot:run
```

---

## Возможные проблемы

### Ошибка SSL при подключении к БД
Railway требует SSL. В `DatabaseConfig.java` уже добавлен `?sslmode=require`.  
Если видите `SSL connection is required` — это нормально при первом запуске, Liquibase подключится через SSL.

### `BotApiException: Conflict`
Означает, что бот уже запущен где-то ещё (например, локально). Остановите локальный инстанс.

### Out of Memory
Измените в Railway: **Settings → Resources** → увеличьте RAM до 512MB.  
Или добавьте переменную: `JAVA_OPTS=-Xms64m -Xmx384m`

---

## Структура переменных Railway (итог)

```
DATABASE_URL      ← автоматически от PostgreSQL-аддона
PORT              ← автоматически от Railway
TELEGRAM_BOT_TOKEN     ← добавляете вручную
TELEGRAM_BOT_USERNAME  ← добавляете вручную
```
