FROM maven:3.9-eclipse-temurin-21 AS bau
WORKDIR /bau
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=bau /bau/target/cockpit.jar cockpit.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Xms128m", "-Xmx384m", "-jar", "cockpit.jar"]
