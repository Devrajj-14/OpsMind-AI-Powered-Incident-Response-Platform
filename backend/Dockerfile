FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q package

FROM eclipse-temurin:21-jre
RUN addgroup --system opsmind && adduser --system --ingroup opsmind opsmind
WORKDIR /app
COPY --from=build /workspace/target/opsmind-backend-1.0.0.jar app.jar
USER opsmind
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar","--spring.profiles.active=production"]
