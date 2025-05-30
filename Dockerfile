# Use a base image with Java 21
FROM openjdk:21-jdk-slim AS builder

# Set working directory
WORKDIR /app

# Copy project files
COPY . .

# Install Python and dependencies for the scripts
RUN apt-get update && apt-get install -y python3 python3-pip && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip3 install -r requirements.txt

# Build the application with Maven
RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests clean install

# Final stage: Run the application
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=builder /app/target/echotype-0.0.1-SNAPSHOT.jar .
CMD ["java", "-jar", "echotype-0.0.1-SNAPSHOT.jar"]
