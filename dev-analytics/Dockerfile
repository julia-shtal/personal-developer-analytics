# Stage 1 — build the React SPA
FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --silent
COPY frontend/ ./
# Vite outDir is ../src/main/resources/static — output lands at /app/src/main/resources/static/
RUN npm run build

# Stage 2 — build the Spring Boot fat JAR
FROM maven:3.9-eclipse-temurin-17-alpine AS backend
WORKDIR /app
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q
COPY src/ src/
# Overwrite static/ with the pre-built SPA
COPY --from=frontend /app/src/main/resources/static/ src/main/resources/static/
RUN ./mvnw package -DskipTests -q

# Stage 3 — minimal runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend /app/target/dev-analytics-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
