# Build stage
FROM docker.io/library/ibm-semeru-runtimes:open-25-jdk AS builder
USER root
WORKDIR /build

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B clean package -DskipTests

# Run stage
FROM docker.io/library/ibm-semeru-runtimes:open-25-jre AS runner
WORKDIR /deployments

COPY --from=builder --chown=185:root /build/target/*-SNAPSHOT.jar app.jar

# application.properties imports ./.env relative to the working directory. It is declared optional,
# so real environment variables can be passed in instead, and those take precedence over this file.
COPY --chown=185:root .env .env

EXPOSE 4000

ENTRYPOINT ["java", "-jar", "app.jar"]
