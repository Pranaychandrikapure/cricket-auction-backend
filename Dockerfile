# Stage 1: Build the application
FROM maven:3-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build the jar file (skipping tests for faster deployment)
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the compiled jar from build stage
COPY --from=build /app/target/realtime-auction-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose standard port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
