# Build context is the parent directory (contains both expense-tracker-backend and
# expense-tracker-frontend) -- see docker-compose.yml's build.context/dockerfile, or
# build directly with: docker build -f expense-tracker-backend/Dockerfile ..

# ---- Frontend build stage ----
FROM node:22-alpine AS frontend-build
WORKDIR /frontend

COPY ../expense-tracker-frontend/package.json ../expense-tracker-frontend/package-lock.json ./
RUN npm ci

COPY ../expense-tracker-frontend/ ./
# Same-origin deployment: the SPA is served by this same backend, so a relative base
# URL is all it needs -- baked in at build time since it's a static bundle.
ENV VITE_API_BASE_URL=/api/v1
RUN npm run build

# ---- Backend build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY expense-tracker-backend/.mvn/ .mvn/
COPY expense-tracker-backend/mvnw expense-tracker-backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY expense-tracker-backend/src ./src
# Bundle the built SPA into Spring Boot's default static-resource location so the
# single jar serves both the API and the frontend (see config.SpaWebConfig).
COPY --from=frontend-build /frontend/dist ./src/main/resources/static
RUN ./mvnw -q -B package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin appuser \
    && mkdir -p /app/storage/statements \
    && chown -R appuser:appuser /app

COPY --from=build /build/target/*.jar app.jar

USER appuser
EXPOSE 8085

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
