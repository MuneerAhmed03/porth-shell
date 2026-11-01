FROM eclipse-temurin:17-jdk-jammy

ARG DEBIAN_FRONTEND=noninteractive
ARG ANDROID_API_LEVEL=34
ARG ANDROID_BUILD_TOOLS=34.0.0
ARG ANDROID_CMDLINE_TOOLS=11076708
ARG ANDROID_CMDLINE_TOOLS_SHA256
ARG PORTH_BUILDER_VERSION=dev
ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="Porth Android Builder" \
      org.opencontainers.image.description="Containerized Android builder for Porth shell APK packaging" \
      org.opencontainers.image.version="${PORTH_BUILDER_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.source="https://github.com/MuneerAhmed03/porth"

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/build-tools/${ANDROID_BUILD_TOOLS}:${PATH}
ENV GRADLE_USER_HOME=/home/porth/.gradle

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        curl \
        git \
        jq \
        python3 \
        unzip \
        zip \
        openssl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 10001 porth \
    && useradd --uid 10001 --gid 10001 --create-home --shell /bin/bash porth

RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" /opt/porth /work/input /work/output "${GRADLE_USER_HOME}"

RUN curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS}_latest.zip" -o /tmp/cmdline-tools.zip \
    && test -n "${ANDROID_CMDLINE_TOOLS_SHA256}" \
    && echo "${ANDROID_CMDLINE_TOOLS_SHA256}  /tmp/cmdline-tools.zip" | sha256sum -c - \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-${ANDROID_API_LEVEL}" \
        "build-tools;${ANDROID_BUILD_TOOLS}"

WORKDIR /opt/porth/porth-shell

# Copy the shell project so the image contains the Android build template.
# Build from the porth-shell repository root:
#   docker build -f builder/Dockerfile -t porth-builder .
COPY . /opt/porth/porth-shell/

# Point Gradle/AGP to the SDK path inside the image.
RUN printf "sdk.dir=%s\n" "${ANDROID_SDK_ROOT}" > /opt/porth/porth-shell/local.properties \
    && chmod +x /opt/porth/porth-shell/gradlew \
        /opt/porth/porth-shell/builder/build-apk.sh \
        /opt/porth/porth-shell/builder/normalize-web-assets.py

RUN chown -R porth:porth /opt/porth /work /home/porth

USER porth

RUN ./gradlew --no-daemon tasks >/dev/null

WORKDIR /work

CMD ["/opt/porth/porth-shell/builder/build-apk.sh"]
