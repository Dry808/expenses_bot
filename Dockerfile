# ── Стадия сборки ─────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Кешируем зависимости отдельным слоем
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Собираем приложение
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Стадия запуска ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Непривилегированный пользователь
RUN addgroup -S botgroup && adduser -S botuser -G botgroup

COPY --from=build /app/target/expense-tracker-bot-*.jar app.jar
RUN chown botuser:botgroup app.jar
USER botuser

# Railway динамически назначает PORT
EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
