FROM docker.io/maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /usr/app
COPY src ./src
COPY pom.xml ./
RUN mvn package -DskipTests

# Package stage
FROM docker.io/eclipse-temurin:21-jre-alpine
COPY --from=build /usr/app/target/*-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]