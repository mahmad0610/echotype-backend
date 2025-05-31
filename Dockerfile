# Stage 1: Build the application
FROM openjdk:21-jdk-slim AS builder

WORKDIR /app

# Install system dependencies for Python and Whisper
RUN apt-get update && apt-get install -y python3 python3-venv python3-dev ffmpeg libsndfile1 && rm -rf /var/lib/apt/lists/*

# Create and activate a virtual environment
RUN python3 -m venv venv
COPY requirements.txt .
RUN ./venv/bin/pip install --upgrade pip
RUN ./venv/bin/pip install -r requirements.txt

# Copy project files and build the Spring Boot app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests clean install

# Stage 2: Run the application
FROM openjdk:21-jdk-slim
WORKDIR /app

# Install Python and dependencies in the final stage
RUN apt-get update && apt-get install -y python3 python3-venv ffmpeg libsndfile1 && rm -rf /var/lib/apt/lists/*

# Copy the built JAR and virtual environment
COPY --from=builder /app/target/echotype-0.0.1-SNAPSHOT.jar .
COPY --from=builder /app/venv ./venv

# Ensure the virtual environment Python is used
ENV PATH="/app/venv/bin:$PATH"

# Verify Python installation (for debugging)
RUN python3 --version || echo "Python3 not found"

CMD ["java", "-jar", "echotype-0.0.1-SNAPSHOT.jar"]
