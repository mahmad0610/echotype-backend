FROM openjdk:21-jdk-slim AS builder
WORKDIR /app
RUN apt-get update && apt-get install -y python3 python3-venv python3-dev ffmpeg libsndfile1 && rm -rf /var/lib/apt/lists/*
RUN python3 -m venv venv
COPY requirements.txt .
RUN ./venv/bin/pip install --upgrade pip
RUN ./venv/bin/pip install -r requirements.txt
COPY . .
RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests clean install

FROM openjdk:21-jdk-slim
WORKDIR /app
RUN apt-get update && apt-get install -y python3 python3-venv ffmpeg libsndfile1 && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/target/echotype-0.0.1-SNAPSHOT.jar .
COPY --from=builder /app/venv ./venv
ENV PATH="/app/venv/bin:$PATH"
CMD ["java", "-jar", "echotype-0.0.1-SNAPSHOT.jar"]
