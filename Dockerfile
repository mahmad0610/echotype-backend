FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests clean install

FROM openjdk:21-jdk-slim
WORKDIR /app
RUN apt-get update && apt-get install -y python3 python3-pip ffmpeg libsndfile1 wget && rm -rf /var/lib/apt/lists/*
RUN wget -O get-pip.py https://bootstrap.pypa.io/get-pip.py && python3 get-pip.py && rm get-pip.py
COPY requirements.txt .
RUN pip3 install --no-cache-dir -r requirements.txt && rm -rf ~/.cache/pip
COPY --from=builder /app/target/echotype-0.0.1-SNAPSHOT.jar .
COPY --from=builder /app/src/main/resources/scripts ./scripts
CMD ["java", "-jar", "echotype-0.0.1-SNAPSHOT.jar"]
