FROM ghcr.io/navikt/baseimages/temurin:20

ENV JAVA_OPTS="-XX:-OmitStackTraceInFastThrow \
               -Xms768m -Xmx1280m"

COPY /build/libs/sosialhjelp-avtaler-api-all.jar app.jar
