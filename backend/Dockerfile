FROM maven:3-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM node:22.17.0-slim AS node22

FROM centos:7

RUN curl -o /etc/yum.repos.d/CentOS-Base.repo https://mirrors.aliyun.com/repo/Centos-7.repo && \
    yum install -y epel-release && \
    curl -o /etc/yum.repos.d/epel.repo https://mirrors.aliyun.com/repo/epel-7.repo && \
    yum clean all && yum makecache

RUN yum update -y && \
    yum install -y git rsync unzip zip curl && \
    yum clean all

ENV SDKMAN_DIR=/usr/local/sdkman
RUN curl -s "https://get.sdkman.io" | bash && \
    echo "export SDKMAN_DIR=${SDKMAN_DIR}" >> /etc/bash.bashrc && \
    echo '[[ -s "${SDKMAN_DIR}/bin/sdkman-init.sh" ]] && source "${SDKMAN_DIR}/bin/sdkman-init.sh"' >> /etc/bash.bashrc

RUN /bin/bash -c 'source "$SDKMAN_DIR/bin/sdkman-init.sh" && \
    sdk install maven 3.9.16 && \
    sdk default maven 3.9.16 && \
    ln -sf "$SDKMAN_DIR/candidates/maven/current/bin/mvn" /usr/local/bin/mvn'

RUN /bin/bash -c 'source "$SDKMAN_DIR/bin/sdkman-init.sh" && \
    sdk install java 17.0.14-tem && \
    sdk install java 25.0.3-tem && \
    sdk default java 25.0.3-tem && \
    ln -sf "$SDKMAN_DIR/candidates/java/current/bin/java" /usr/local/bin/java'

COPY --from=node22 /usr/local/bin/node /usr/local/bin/
COPY --from=node22 /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm
RUN ln -sf /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && \
    ln -sf /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

ENV NVM_DIR=/usr/local/nvm
RUN git clone --depth 1 --branch v0.40.6 https://github.com/nvm-sh/nvm.git $NVM_DIR

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
