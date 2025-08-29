# Use Confluent Kafka Connect base image
FROM confluentinc/cp-kafka-connect:7.5.0

# Set working directory
WORKDIR /kafka/plugins

# Switch to root user to install packages
USER root

# Install utilities
RUN microdnf install -y curl unzip && rm -rf /var/cache/yum

# Install Kafka Connect plugins

# Install Confluent JDBC Sink Connector
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-jdbc:10.5.0 --component-dir /kafka/plugins \
    || echo "JDBC installation failed!"

# Install Debezium SQL Server Source Connector
RUN curl -fsSL https://repo1.maven.org/maven2/io/debezium/debezium-connector-sqlserver/2.5.1.Final/debezium-connector-sqlserver-2.5.1.Final-plugin.tar.gz -o /tmp/debezium-connector-sqlserver.tar.gz && \
    mkdir -p /kafka/plugins/debezium-sqlserver && \
    tar -xzf /tmp/debezium-connector-sqlserver.tar.gz -C /kafka/plugins/debezium-sqlserver --strip-components=1 && \
    rm /tmp/debezium-connector-sqlserver.tar.gz

# Install Debezium JDBC Sink Connector
RUN curl -fsSL https://repo1.maven.org/maven2/io/debezium/debezium-connector-jdbc/2.5.1.Final/debezium-connector-jdbc-2.5.1.Final-plugin.tar.gz -o /tmp/debezium-connector-jdbc.tar.gz && \
    mkdir -p /kafka/plugins/debezium-jdbc && \
    tar -xzf /tmp/debezium-connector-jdbc.tar.gz -C /kafka/plugins/debezium-jdbc --strip-components=1 && \
    rm /tmp/debezium-connector-jdbc.tar.gz

# (Optional) Copy your custom SMTs if needed
COPY kafka-connect-smt/target/kafka-connect-smt-1.0-SNAPSHOT.jar /kafka/plugins/

# Install OpenTelemetry Java Agent
ENV OTEL_JAVA_AGENT_VERSION=1.30.0
RUN curl -fsSL https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent-${OTEL_JAVA_AGENT_VERSION}.jar \
    -o /kafka/plugins/opentelemetry-javaagent.jar

# Install OpenTelemetry Core SDK + API libraries
RUN curl -fsSL https://repo1.maven.org/maven2/io/opentelemetry/opentelemetry-api/1.30.0/opentelemetry-api-1.30.0.jar -o /kafka/plugins/opentelemetry-api-1.30.0.jar && \
    curl -fsSL https://repo1.maven.org/maven2/io/opentelemetry/opentelemetry-context/1.30.0/opentelemetry-context-1.30.0.jar -o /kafka/plugins/opentelemetry-context-1.30.0.jar && \
    curl -fsSL https://repo1.maven.org/maven2/io/opentelemetry/opentelemetry-sdk/1.30.0/opentelemetry-sdk-1.30.0.jar -o /kafka/plugins/opentelemetry-sdk-1.30.0.jar

# Configure Kafka Connect environment
ENV CONNECT_PLUGIN_PATH="/kafka/plugins"

# Set OpenTelemetry environment variables
ENV OTEL_SERVICE_NAME=debezium-connect
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://seq:5341
ENV OTEL_TRACES_EXPORTER=otlp
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none
ENV OTEL_PROPAGATORS=tracecontext,baggage
ENV OTEL_INSTRUMENTATION_COMMON_DEFAULT_ENABLED=false
ENV OTEL_LOG_LEVEL=debug

# Proper Java agent injection into Kafka Connect JVM
ENV KAFKA_OPTS="-javaagent:/kafka/plugins/opentelemetry-javaagent.jar"
ENV CONNECT_HEAP_OPTS="-Xms256M -Xmx2G"

# Switch back to appuser for safety
USER appuser

# Expose Kafka Connect REST API
EXPOSE 8083

# Start Kafka Connect
CMD ["/etc/confluent/docker/run"]
