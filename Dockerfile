# Build the Java 21 application and collect its runtime dependencies.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./pom.xml
COPY src ./src
# Deployment does not need to compile or execute the local test suite.
RUN mvn -B -Dmaven.test.skip=true package dependency:copy-dependencies -DoutputDirectory=target/lib \
    && test -d target/classes \
    && test -d target/lib

# Small runtime image.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/classes ./build
COPY --from=build /app/target/lib ./lib
COPY public ./public
ENV HOST=0.0.0.0 COOKIE_SECURE=true
EXPOSE 8080
CMD ["java", "-cp", "build:lib/*", "courier.Server"]
