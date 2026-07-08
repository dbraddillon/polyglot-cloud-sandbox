# Multi-stage build - the same idea as a multi-stage .NET Dockerfile (an `sdk` image to build,
# an `aspnet` image to actually run): keeps the final image from carrying the whole Maven/JDK
# toolchain around.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
