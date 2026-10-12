FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests \
    && cp target/backend-0.0.1-SNAPSHOT.jar /app/application.jar

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/application.jar ./application.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar /app/application.jar"]
