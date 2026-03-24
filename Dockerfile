FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy pre-built JAR (run 'mvn clean package -DskipTests' locally first)
COPY target/work-flow-engine-1.0.0.jar workflow-engine.jar

# Health check
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/api/workflows || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "workflow-engine.jar"]
