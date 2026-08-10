FROM maven:3-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

FROM node:22.17.0-slim AS node22

FROM rockylinux:9

# 切换为阿里云镜像源（国内加速）
RUN rm -f /etc/yum.repos.d/*.repo /usr/lib/yum.repos.d/*.repo && \
    tee /etc/yum.repos.d/rocky-aliyun.repo > /dev/null <<'EOF'
[baseos]
name=Rocky Linux $releasever - BaseOS
baseurl=https://mirrors.aliyun.com/rockylinux/$releasever/BaseOS/$basearch/os/
gpgcheck=1
enabled=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-Rocky-9

[appstream]
name=Rocky Linux $releasever - AppStream
baseurl=https://mirrors.aliyun.com/rockylinux/$releasever/AppStream/$basearch/os/
gpgcheck=1
enabled=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-Rocky-9

[extras]
name=Rocky Linux $releasever - Extras
baseurl=https://mirrors.aliyun.com/rockylinux/$releasever/extras/$basearch/os/
gpgcheck=1
enabled=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-Rocky-9
EOF

RUN yum update -y && \
    yum install -y --allowerasing git rsync unzip zip curl && \
    yum clean all

ENV SDKMAN_DIR=/usr/local/sdkman
RUN curl -s "https://get.sdkman.io" | bash && \
    echo "export SDKMAN_DIR=${SDKMAN_DIR}" >> /etc/bashrc && \
    echo '[[ -s "${SDKMAN_DIR}/bin/sdkman-init.sh" ]] && source "${SDKMAN_DIR}/bin/sdkman-init.sh"' >> /etc/bashrc

RUN /bin/bash -c 'source "$SDKMAN_DIR/bin/sdkman-init.sh" && \
    sdk install maven 3.9.16 && \
    sdk default maven 3.9.16 && \
    ln -sf "$SDKMAN_DIR/candidates/maven/current/bin/mvn" /usr/local/bin/mvn'

RUN /bin/bash -c 'source "$SDKMAN_DIR/bin/sdkman-init.sh" && \
    sdk install java 17.0.20-tem && \
    sdk install java 25.0.3-tem && \
    sdk default java 25.0.3-tem && \
    ln -sf "$SDKMAN_DIR/candidates/java/current/bin/java" /usr/local/bin/java'

COPY --from=node22 /usr/local/bin/node /usr/local/bin/
COPY --from=node22 /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm
RUN ln -sf /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && \
    ln -sf /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

ENV NVM_DIR=/usr/local/nvm
RUN git clone --depth 1 --branch v0.40.6 https://github.com/nvm-sh/nvm.git $NVM_DIR && \
    echo "export NVM_DIR=${NVM_DIR}" >> /etc/bashrc && \
    echo '[ -s "${NVM_DIR}/nvm.sh" ] && \. "${NVM_DIR}/nvm.sh"' >> /etc/bashrc

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
