FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copia só o pom.xml primeiro
COPY pom.xml .

# Baixa as dependências (vai ser cacheado enquanto pom.xml não mudar)
RUN mvn dependency:go-offline

# Agora copia o código fonte
COPY src ./src

# Compila o projeto e gera o JAR
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
