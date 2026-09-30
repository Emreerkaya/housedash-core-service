FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon --version
COPY config config
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 housedash
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER 10001
ENV SPRING_PROFILES_ACTIVE=supabase
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
