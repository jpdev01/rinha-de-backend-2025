# syntax=docker/dockerfile:1.4
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Copia só o pom.xml primeiro
COPY pom.xml .

# Baixa as dependências com cache para acelerar rebuilds
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline

# Copia o código fonte
COPY src ./src

# Compila o projeto e gera o JAR com cache
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
