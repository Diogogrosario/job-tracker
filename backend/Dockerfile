FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
RUN addgroup -S app \
    && adduser -S -G app app

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
