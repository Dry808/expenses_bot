# ── Стадия сборки ─────────────────────────────────────────────
 FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
 WORKDIR /app

 # Сначала копируем pom.xml — чтобы кешировать зависимости
 COPY pom.xml .
 RUN mvn dependency:go-offline -B

 # Копируем исходники и собираем
 COPY src ./src
 RUN mvn clean package -DskipTests -B

 # ── Стадия запуска ─────────────────────────────────────────────
 FROM eclipse-temurin:21-jre-alpine
 WORKDIR /app

 # Создаём непривилегированного пользователя
 RUN addgroup -S botgroup && adduser -S botuser -G botgroup

 COPY --from=build /app/target/expense-tracker-bot-*.jar app.jar

 RUN chown botuser:botgroup app.jar
 USER botuser

 EXPOSE 8080

 ENTRYPOINT ["java", "-jar", "app.jar"]