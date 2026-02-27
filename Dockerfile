# ── Stage 1: Compile ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy source files
COPY src/ src/

# Compile all Java files into /app/out
RUN mkdir -p out && \
    javac -d out src/com/analyzer/*.java

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy compiled classes from build stage
COPY --from=build /app/out ./out

# Copy frontend static files
COPY frontend/ ./frontend/

# Render assigns a PORT env variable — default to 8080 locally
ENV PORT=8080

EXPOSE 8080

# Pass $PORT to the server at runtime
CMD ["sh", "-c", "java -cp out com.analyzer.ThreadDumpServer $PORT"]
