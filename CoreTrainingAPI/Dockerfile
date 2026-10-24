# Build stage
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom trước để cache dependency riêng
COPY pom.xml .

# Cache Maven repository giữa các lần Docker build
RUN mvn dependency:go-offline -B

# Chỉ copy source sau khi dependency đã được cache
COPY src ./src

# Cache Maven repository cho bước package
RUN mvn clean package -DskipTests -B


# Run stage
FROM eclipse-temurin:17-jre-jammy

# Inspect container and codecs before accepting browser video uploads.
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
