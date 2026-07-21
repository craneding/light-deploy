FROM maven:3-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage to get Node.js binaries (avoid tar extraction issues on some systems)
FROM node:20-slim AS node

FROM eclipse-temurin:17-jre

# System dependencies: git/rsync for deploy engine
RUN apt-get update && apt-get install -y git rsync && \
    rm -rf /var/lib/apt/lists/*

# Copy Node.js from official image (no tar extraction needed)
COPY --from=node /usr/local/bin/node /usr/local/bin/
COPY --from=node /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm
RUN ln -sf /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && \
    ln -sf /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

# Install nvm for Node.js version management
ENV NVM_DIR=/usr/local/nvm
RUN git clone --depth 1 --branch v0.40.1 https://github.com/nvm-sh/nvm.git $NVM_DIR

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
