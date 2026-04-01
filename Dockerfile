# Spring Boot API + 爬虫（Python），用于 Render 等容器部署
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build
COPY spring-backend/pom.xml spring-backend/pom.xml
COPY spring-backend/src spring-backend/src
RUN cd spring-backend && mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-pip python3-venv \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/spring-backend/target/*.jar /app/app.jar
COPY 爬虫 /app/crawler
RUN cd /app/crawler && python3 -m venv .venv && .venv/bin/pip install --no-cache-dir -r requirements.txt
ENV NBA_CRAWLER_HOME=/app/crawler
EXPOSE 8080
CMD ["sh", "-c", "exec java -jar /app/app.jar"]
