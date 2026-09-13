FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src ./src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --create-home --shell /usr/sbin/nologin fitnesshub
COPY --from=build /app/target/fitnesshub-backend-*.jar app.jar
USER fitnesshub
EXPOSE 8080

# The JVM defaults to a heap of 25% of the container limit, which on a 512 MB
# free-tier instance is 128 MB - too small for this app, and it will spend its
# time in GC or die outright. Override by setting JAVA_TOOL_OPTIONS.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-jar", "app.jar"]
