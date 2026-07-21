FROM maven:3-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage to get Node.js binaries (avoid tar extraction issues on some systems)
FROM node:20-slim AS node

FROM eclipse-temurin:17-jdk

# System dependencies: git/rsync for deploy engine, unzip/zip/curl for SDKMAN
RUN apt-get update && apt-get install -y git rsync unzip zip curl && \
    rm -rf /var/lib/apt/lists/*

# Install SDKMAN for Java version management
ENV SDKMAN_DIR=/usr/local/sdkman
RUN curl -s "https://get.sdkman.io" | bash && \
    echo "export SDKMAN_DIR=${SDKMAN_DIR}" >> /etc/bash.bashrc && \
    echo '[[ -s "${SDKMAN_DIR}/bin/sdkman-init.sh" ]] && source "${SDKMAN_DIR}/bin/sdkman-init.sh"' >> /etc/bash.bashrc

# Pre-install Maven (default for Java build scripts)
RUN /bin/bash -c 'source "$SDKMAN_DIR/bin/sdkman-init.sh" && \
    sdk install maven 3.9.9 && \
    sdk default maven 3.9.9 && \
    ln -sf "$SDKMAN_DIR/candidates/maven/current/bin/mvn" /usr/local/bin/mvn'

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
