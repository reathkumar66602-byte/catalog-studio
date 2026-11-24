FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY platform platform
COPY catalog-studio-backend catalog-studio-backend
COPY services services
COPY gateway gateway
RUN mvn -pl services/supplier-service -am -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/supplier-service/target/supplier-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
