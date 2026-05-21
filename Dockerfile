FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21

ENV JAVA_OPTS="-XX:-OmitStackTraceInFastThrow \
                       -Xms768m -Xmx1280m"

COPY /build/libs/sosialhjelp-avtaler-api-all.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
