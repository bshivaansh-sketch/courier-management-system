FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package dependency:copy-dependencies -DoutputDirectory=target/lib

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/classes ./build
COPY --from=build /app/target/lib ./lib
COPY public ./public
ENV HOST=0.0.0.0 COOKIE_SECURE=true
EXPOSE 8080
CMD ["java", "-cp", "build:lib/*", "courier.Server"]
