# Multi-stage Docker build for lightweight and fast production deployment
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN javac CampusComplaintSystem.java

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app /app
ENV PORT=8090
EXPOSE 8090
CMD ["java", "CampusComplaintSystem"]
