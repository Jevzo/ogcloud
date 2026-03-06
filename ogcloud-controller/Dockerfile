FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY ogcloud-controller/build/libs/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
