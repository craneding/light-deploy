FROM maven:3-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage to get Node.js binaries (avoid tar extraction issues on some systems)
FROM node:20-slim AS node20

FROM eclipse-temurin:17-jdk

# System dependencies: git/rsync for deploy engine, unzip/zip/curl for SDKMAN
RUN apt-get update && apt-get install -y git rsync unzip zip curl python3 && \
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

# Pre-install Java 25 (for projects that require it)
RUN /bin/bash -c 'source "$SDKMAN_DIR/bin/sdkman-init.sh" && \
    sdk install java 25.0.3-tem'

# Copy Node.js 20 from official image (no tar extraction needed)
COPY --from=node20 /usr/local/bin/node /usr/local/bin/
COPY --from=node20 /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm
RUN ln -sf /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && \
    ln -sf /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

# Install nvm for Node.js version management
ENV NVM_DIR=/usr/local/nvm
RUN git clone --depth 1 --branch v0.40.1 https://github.com/nvm-sh/nvm.git $NVM_DIR

# tar wrapper: Python tarfile bypasses overlay2 ENOSYS on CentOS 7
RUN <<WRAPBUILD
set -e
cat > /usr/local/lib/tar-wrapper.py << 'WRAPPER'
#!/usr/bin/env python3
import os, sys, tarfile, fnmatch
REAL_TAR = '/bin/tar'

def is_extraction(args):
    return any(flag in a for a in args for flag in ('-x', '--extract', '--get'))

def parse(args):
    archive = target = None; strip = 0; excludes = []; i = 0
    while i < len(args):
        a = args[i]
        if a == '-C' and i + 1 < len(args): target = args[i+1]; i += 1
        elif a.startswith('--strip-components='): strip = int(a.split('=', 1)[1])
        elif a == '--strip-components' and i + 1 < len(args): strip = int(args[i+1]); i += 1
        elif a.startswith('--exclude='): excludes.append(a.split('=', 1)[1])
        elif a == '--exclude' and i + 1 < len(args): excludes.append(args[i+1]); i += 1
        elif 'f' in a.lstrip('-') and len(a) >= 2:
            idx = a.index('f')
            suffix = a[idx+1:]
            if suffix and not suffix.startswith('-'): archive = suffix
            elif i + 1 < len(args): archive = args[i+1]; i += 1
        i += 1
    return archive, target, strip, excludes

def extract(archive, target, strip, excludes):
    os.makedirs(target, exist_ok=True)
    with tarfile.open(archive, 'r:*') as tar:
        members = tar.getmembers()
        if strip:
            for m in members:
                parts = m.name.split('/', strip + 1)
                m.name = '/'.join(parts[strip:]) if len(parts) > strip else ''
            members = [m for m in members if m.name]
        if excludes:
            members = [m for m in members if not any(fnmatch.fnmatch(m.name, e) or m.name.startswith(e) for e in excludes)]
        tar.extractall(target, members=members)

def main():
    args = sys.argv[1:]
    if not args: os.execvp(REAL_TAR, ['tar'])
    if is_extraction(args):
        archive, target, strip, excludes = parse(args)
        if archive and target: extract(archive, target, strip, excludes); return
    os.execvp(REAL_TAR, ['tar'] + args)

if __name__ == '__main__': main()
WRAPPER
chmod +x /usr/local/lib/tar-wrapper.py
# Override tar in PATH with wrapper shell script
printf '#!/bin/bash\nexec /usr/local/lib/tar-wrapper.py "$@"\n' > /usr/local/bin/tar
chmod +x /usr/local/bin/tar
WRAPBUILD

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
