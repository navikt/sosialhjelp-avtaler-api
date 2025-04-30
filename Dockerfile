FROM gcr.io/distroless/java21-debian12

ENV JAVA_OPTS="-XX:-OmitStackTraceInFastThrow \
               -Xms768m -Xmx1280m"

COPY /build/libs/sosialhjelp-avtaler-api-all.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
