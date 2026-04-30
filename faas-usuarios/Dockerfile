FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app

# Instalar Node.js y Azure Functions Core Tools en el contenedor
RUN apt-get update && apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && \
    apt-get install -y nodejs && \
    npm i -g azure-functions-core-tools@4 --unsafe-perm true

COPY . .
RUN mvn clean package -DskipTests

FROM mcr.microsoft.com/azure-functions/java:4-java17
WORKDIR /home/site/wwwroot
COPY --from=build /app/target/azure-functions/B6FuncionTex/ .