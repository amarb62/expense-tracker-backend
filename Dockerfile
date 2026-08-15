# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src ./src
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
