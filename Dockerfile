# Etapa 1: Build com Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build

LABEL authors="jptruchinski@gmail.com"

# Diretório de trabalho dentro do container
WORKDIR /app

# Copia os arquivos do projeto para dentro da imagem
COPY pom.xml .
COPY src ./src

# Compila o projeto e gera o JAR
RUN mvn clean package -DskipTests

# Etapa 2: Imagem final mais leve, apenas com o JAR
FROM eclipse-temurin:17-jdk-alpine

RUN apk add --no-cache curl

# Diretório de trabalho no container final
WORKDIR /app

# Copia o JAR da etapa anterior
COPY --from=build /app/target/*.jar app.jar

# Expõe a porta (altere se sua aplicação usar outra)
EXPOSE 8080

# Comando para rodar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]