FROM eclipse-temurin:17-jre-alpine

ENV TZ=UTC

# Set the working directory in the container
WORKDIR /app

# Copy the executable JAR into the container
# Note: Ensure the build action removes any plain.jar before this step
COPY build/libs/*.jar app.jar

# Expose port 8080
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "app.jar"]
