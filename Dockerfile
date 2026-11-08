# 1) SPA: built here because src/main/resources/static/assets is git-ignored (Render builds from git).
FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY src/frontend/package.json src/frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY src/frontend ./
RUN npm run build

# 2) Fat JAR
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon > /dev/null
COPY src ./src
COPY --from=frontend /frontend/dist/ ./src/main/resources/static/
RUN ./gradlew bootJar -x test --no-daemon

# 3) Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S kikomo && adduser -S kikomo -G kikomo
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
USER kikomo
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
