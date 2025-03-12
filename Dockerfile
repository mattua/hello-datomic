FROM maven:3.8.6-openjdk-11 as builder



# Copy POM and source code
COPY pom.xml .
COPY src/ ./src/

# Build the application
RUN mvn clean package -DskipTests

# Verify the JAR was created
RUN ls -la target/

# Runtime image
FROM openjdk:11-jre-slim

# Copy the built jar from the builder stage
COPY --from=builder /target/datomic-demo-1.0-SNAPSHOT.jar ./app.jar

# Wait for Datomic to be ready and then run the application
CMD ["java", "-jar", "app.jar"]