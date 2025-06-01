FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests clean install

FROM openjdk:21-jdk-slim
WORKDIR /app

# Install required dependencies and clean up cache
RUN apt-get update && apt-get install -y python3 python3-venv ffmpeg libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

# Set up Python virtual environment
RUN python3 -m venv /app/venv
ENV PATH="/app/venv/bin:$PATH"

# Upgrade pip
RUN /app/venv/bin/pip install --upgrade pip

# Copy requirements file and install dependencies with extra index URL
COPY requirements.txt .
RUN /app/venv/bin/pip install --no-cache-dir --extra-index-url https://download.pytorch.org/whl/cpu -r requirements.txt && rm -rf ~/.cache/pip

# Copy built artifacts and scripts
COPY --from=builder /app/target/echotype-0.0.1-SNAPSHOT.jar .
COPY --from=builder /app/src/main/resources/scripts ./scripts

# Define default command
CMD ["java", "-jar", "echotype-0.0.1-SNAPSHOT.jar"]
