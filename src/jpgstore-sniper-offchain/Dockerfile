FROM eclipse-temurin:21-jdk-alpine
ADD ./build/libs/*.jar /app/app.jar
WORKDIR /app
ENTRYPOINT ["java", "-jar", "app.jar"]
