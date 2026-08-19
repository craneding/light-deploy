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

# 从华为云镜像手动下载 JDK/Maven（比 sdk install 的 GitHub 源快），再注册为 sdkman 本地版本
ARG TARGETARCH
RUN set -eux; \
    case "${TARGETARCH:-$(uname -m)}" in \
      amd64|x86_64) JDK_ARCH=linux-x64 ;; \
      arm64|aarch64) JDK_ARCH=linux-aarch64 ;; \
      *) echo "Unsupported architecture: ${TARGETARCH}"; exit 1 ;; \
    esac; \
    curl -fsSL -o /tmp/jdk17.tar.gz "https://mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_${JDK_ARCH}_bin.tar.gz"; \
    mkdir -p /opt/jdk17; \
    tar -xzf /tmp/jdk17.tar.gz -C /opt/jdk17 --strip-components=1; \
    curl -fsSL -o /tmp/jdk25.tar.gz "https://mirrors.huaweicloud.com/openjdk/25.0.2/openjdk-25.0.2_${JDK_ARCH}_bin.tar.gz"; \
    mkdir -p /opt/jdk25; \
    tar -xzf /tmp/jdk25.tar.gz -C /opt/jdk25 --strip-components=1; \
    curl -fsSL -o /tmp/maven.tar.gz "https://mirrors.huaweicloud.com/apache/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz"; \
    mkdir -p /opt/maven; \
    tar -xzf /tmp/maven.tar.gz -C /opt/maven --strip-components=1; \
    rm -f /tmp/jdk17.tar.gz /tmp/jdk25.tar.gz /tmp/maven.tar.gz

RUN /bin/bash -c 'source "$SDKMAN_DIR/bin/sdkman-init.sh" && \
    sdk install java 17-loc /opt/jdk17 && \
    sdk install java 25-loc /opt/jdk25 && \
    sdk default java 25-loc && \
    ln -sf "$SDKMAN_DIR/candidates/java/current/bin/java" /usr/local/bin/java && \
    sdk install maven 3.9.16-loc /opt/maven && \
    sdk default maven 3.9.16-loc && \
    ln -sf "$SDKMAN_DIR/candidates/maven/current/bin/mvn" /usr/local/bin/mvn'

COPY --from=node22 /usr/local/bin/node /usr/local/bin/
COPY --from=node22 /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm
RUN ln -sf /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && \
    ln -sf /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

ENV NVM_DIR=/usr/local/nvm
RUN git clone --depth 1 --branch v0.40.6 https://github.com/nvm-sh/nvm.git $NVM_DIR && \
    echo "export NVM_DIR=${NVM_DIR}" >> /etc/bashrc && \
    echo '[ -s "${NVM_DIR}/nvm.sh" ] && \. "${NVM_DIR}/nvm.sh"' >> /etc/bashrc

# Android 编译环境（SDK 走腾讯云镜像，Gradle 走华为云镜像）
# 注意：Google 官方 cmdline-tools/build-tools 仅提供 linux-x86_64 包，arm64 镜像上 Android 编译不可用
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    PATH="${PATH}:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools"
RUN echo "export ANDROID_HOME=${ANDROID_HOME}" >> /etc/bashrc && \
    echo "export ANDROID_SDK_ROOT=${ANDROID_HOME}" >> /etc/bashrc && \
    echo 'export PATH="$PATH:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"' >> /etc/bashrc

ARG TARGETARCH
RUN set -eux; \
    case "${TARGETARCH:-$(uname -m)}" in \
      amd64|x86_64) ;; \
      arm64|aarch64) echo "WARNING: Android cmdline-tools/build-tools only ship x86_64 Linux binaries, build for amd64 if Android builds are required"; ;; \
      *) echo "Unsupported architecture: ${TARGETARCH}"; exit 1 ;; \
    esac; \
    curl -fsSL -o /tmp/cmdline-tools.zip "https://mirrors.cloud.tencent.com/AndroidSDK/commandlinetools-linux-15859902_latest.zip"; \
    mkdir -p ${ANDROID_HOME}/cmdline-tools; \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools; \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest; \
    curl -fsSL -o /tmp/platform-tools.zip "https://mirrors.cloud.tencent.com/AndroidSDK/platform-tools_r37.0.1-linux.zip"; \
    unzip -q /tmp/platform-tools.zip -d ${ANDROID_HOME}; \
    curl -fsSL -o /tmp/build-tools.zip "https://mirrors.cloud.tencent.com/AndroidSDK/build-tools_r36_linux.zip"; \
    mkdir -p ${ANDROID_HOME}/build-tools /tmp/bt; \
    unzip -q /tmp/build-tools.zip -d /tmp/bt; \
    mv /tmp/bt/android-16 ${ANDROID_HOME}/build-tools/36.0.0; \
    curl -fsSL -o /tmp/platform-36.zip "https://mirrors.cloud.tencent.com/AndroidSDK/platform-36_r02.zip"; \
    mkdir -p ${ANDROID_HOME}/platforms /tmp/p36; \
    unzip -q /tmp/platform-36.zip -d /tmp/p36; \
    mv /tmp/p36/android-36 ${ANDROID_HOME}/platforms/android-36; \
    mkdir -p ${ANDROID_HOME}/licenses; \
    printf "24333f8a63b6825ea9c5514f83c2829b004d1fee\n84831b9409646a918e30573bab4c9c91346d8abd\n" > ${ANDROID_HOME}/licenses/android-sdk-license; \
    printf "84831b9409646a918e30573bab4c9c91346d8abd\n" > ${ANDROID_HOME}/licenses/android-sdk-preview-license; \
    rm -rf /tmp/cmdline-tools.zip /tmp/platform-tools.zip /tmp/build-tools.zip /tmp/platform-36.zip /tmp/bt /tmp/p36

# Gradle（华为云镜像，注册为 sdkman 本地版本）
RUN curl -fsSL -o /tmp/gradle.zip "https://mirrors.huaweicloud.com/gradle/gradle-9.7.0-bin.zip" && \
    mkdir -p /opt/gradle && \
    unzip -q /tmp/gradle.zip -d /opt/gradle && \
    mv /opt/gradle/gradle-9.7.0 /opt/gradle/current && \
    ln -sf /opt/gradle/current/bin/gradle /usr/local/bin/gradle && \
    rm -f /tmp/gradle.zip

RUN /bin/bash -c 'source "$SDKMAN_DIR/bin/sdkman-init.sh" && \
    sdk install gradle 9.7.0-loc /opt/gradle/current && \
    sdk default gradle 9.7.0-loc'

# Gradle 依赖国内镜像加速
RUN mkdir -p /root/.gradle/init.d && tee /root/.gradle/init.d/aliyun-mirror.gradle > /dev/null <<'EOF'
allprojects {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
    }
}
EOF

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
