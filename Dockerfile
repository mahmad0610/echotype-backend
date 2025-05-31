# Use a base image with Java 21
FROM openjdk:21-jdk-slim AS builder

# Set working directory
WORKDIR /app

# Install system dependencies for Python, pip, and whisper
RUN apt-get update && apt-get install -y python3 python3-venv python3-dev ffmpeg libsndfile1 && rm -rf /var/lib/apt/lists/*

# Create a virtual environment and install Python dependencies
RUN python3 -m venv venv
COPY requirements.txt .
RUN ./venv/bin/pip install -r requirements.txt

# Copy project files
COPY . .

# Build the application with Maven
RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests clean install

# Final stage: Run the application with the virtual environment
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=builder /app/target/echotype-0.0.1-SNAPSHOT.jar .
COPY --from=builder /app/venv ./venv
ENV PATH="/app/venv/bin:$PATH"
<<<<<<< HEAD
CMD ["java", "-jar", "echotype-0.0.1-SNAPSHOT.jar"]
=======
CMD ["java", "-jar", "echotype-0.0.1-SNAPSHOT.jar"]
>>>>>>> 6f125e1 (Update Dockerfile to use virtual environment for Python packages)
