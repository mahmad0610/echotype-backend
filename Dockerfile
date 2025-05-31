FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests clean install

FROM openjdk:21-jdk-slim
WORKDIR /app
RUN apt-get update && apt-get install -y python3 python3-venv ffmpeg libsndfile1 && rm -rf /var/lib/apt/lists/*
RUN python3 -m venv /app/venv
RUN /app/venv/bin/pip install --upgrade pip
COPY requirements.txt .
RUN /app/venv/bin/pip install --no-cache-dir -r requirements.txt && rm -rf ~/.cache/pip
COPY --from=builder /app/target/echotype-0.0.1-SNAPSHOT.jar .
COPY --from=builder /app/src/main/resources/scripts ./scripts
ENV PATH="/app/venv/bin:$PATH"
CMD ["java", "-jar", "echotype-0.0.1-SNAPSHOT.jar"]
