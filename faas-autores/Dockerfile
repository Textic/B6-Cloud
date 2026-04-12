FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY . .
RUN mvn clean package

FROM mcr.microsoft.com/azure-functions/java:4-java17
WORKDIR /home/site/wwwroot
COPY --from=build /app/target/azure-functions/B6FuncionTex3/ .
