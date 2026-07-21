FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /workspace
COPY pom.xml .
COPY settings-docker.xml .
RUN mvn -s settings-docker.xml -B -q dependency:go-offline

COPY src ./src
RUN mvn -s settings-docker.xml -B -q package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl python3 python3-pip python-is-python3 \
    && rm -rf /var/lib/apt/lists/*

RUN python -m pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu 'torch==2.3.1+cpu' \
    && python -m pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple \
        'pandas>=2.0' \
        'numpy>=1.26' \
        'scikit-learn>=1.5'

COPY --from=builder /workspace/target/*.jar /app/app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health >/dev/null || exit 1

ENTRYPOINT ["java", "-Xmx768m", "-jar", "/app/app.jar"]
