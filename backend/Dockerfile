# Stage 1: build the jar with the Maven wrapper (full JDK needed to compile).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the build files first, so dependencies are cached until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B dependency:go-offline

COPY src src
# Tests run in CI (they need Docker for Testcontainers), not inside the image build.
RUN ./mvnw -B -DskipTests package

# Stage 2: run the jar on a smaller JRE-only image.
FROM eclipse-temurin:21-jre
# Run as an unprivileged user, so a compromised app can't act as root in the container.
RUN groupadd --system fiber && useradd --system --gid fiber --no-create-home fiber
WORKDIR /app
COPY --from=build /app/target/fiber-backend-*.jar app.jar
USER fiber
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
