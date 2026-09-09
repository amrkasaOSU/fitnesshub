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
ENTRYPOINT ["java", "-jar", "app.jar"]
