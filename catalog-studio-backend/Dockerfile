FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY platform platform
COPY catalog-studio-backend catalog-studio-backend
COPY services services
COPY gateway gateway
RUN mvn -pl catalog-studio-backend -am -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/catalog-studio-backend/target/catalog-studio-backend-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/app.jar"]
