# Stage 1: Build
FROM eclipse-temurin:26-jdk AS build
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw package -DskipTests -B -q

# Stage 2: Runtime
FROM eclipse-temurin:26-jre
WORKDIR /app

COPY --from=build /app/target/scheduler-backend-*.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q --spider http://localhost:8080/api/locations || exit 1

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
