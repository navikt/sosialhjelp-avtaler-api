
object Versions {
    const val konfig_version = "1.6.10.0"
    const val ktor_version = "2.1.3"
    const val kotlin_version = "1.7.20"
    const val kotestVersion = "5.5.4"
    const val ktlint = "0.45.2"
    const val logback_version = "1.4.4"
    const val logback_encoder_version = "7.2"
    const val jackson_version = "2.14.0"
    const val microutils_version = "3.0.4"
    const val token_support_version = "2.1.7"
    const val kotliquery_version = "1.9.0"
    const val flywaydb_version = "9.7.0"
    const val hikari_version = "5.0.1"
    const val postgresql_version = "42.5.0"
    const val digipost_signature_api_client = "7.0-RC4"
    const val jaxb_runtime = "2.3.7"
    const val google_cloud_libraries = "26.1.4"
    const val google_cloud_secretmanager = "2.6.0"
}

plugins {
    application
    kotlin("jvm") version "1.7.10"
    id("io.ktor.plugin") version "2.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "10.3.0"
}

ktlint {
    this.version.set(Versions.ktlint)
}

group = "no.nav.sosialhjelp"
version = "0.0.1"
application {
    mainClass.set("no.nav.sosialhjelp.avtaler.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {

    implementation("com.natpryce:konfig:${Versions.konfig_version}")

    // ktor server
    implementation("io.ktor:ktor-server-core-jvm:${Versions.ktor_version}")
    implementation("io.ktor:ktor-server-cio:${Versions.ktor_version}")
    implementation("io.ktor:ktor-server-netty-jvm:${Versions.ktor_version}")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:${Versions.ktor_version}")
    implementation("io.ktor:ktor-server-auth-jwt:${Versions.ktor_version}")

    // Ktor Client
    implementation("io.ktor:ktor-client-core:${Versions.ktor_version}")
    implementation("io.ktor:ktor-client-cio:${Versions.ktor_version}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktor_version}")
    implementation("io.ktor:ktor-client-auth:${Versions.ktor_version}")
    implementation("io.ktor:ktor-client-jackson:${Versions.ktor_version}")
    implementation("io.ktor:ktor-client-mock:${Versions.ktor_version}")

    implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor_version}")
    implementation("io.ktor:ktor-serialization-jackson-jvm:${Versions.ktor_version}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.jackson_version}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jackson_version}")

    implementation("io.github.microutils:kotlin-logging:${Versions.microutils_version}")
    implementation("ch.qos.logback:logback-classic:${Versions.logback_version}")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:${Versions.logback_encoder_version}")

    // Database
    implementation("org.postgresql:postgresql:${Versions.postgresql_version}")
    implementation("org.flywaydb:flyway-core:${Versions.flywaydb_version}")
    implementation("com.zaxxer:HikariCP:${Versions.hikari_version}")
    implementation("com.github.seratch:kotliquery:${Versions.kotliquery_version}")

    // altinn
    implementation("no.nav.security:token-validation-ktor:${Versions.token_support_version}")
    implementation("no.nav.security:token-client-core:${Versions.token_support_version}")

    // digipost
    implementation("no.digipost.signature:signature-api-client-java:${Versions.digipost_signature_api_client}")
    implementation("com.sun.xml.bind:jaxb-impl:${Versions.jaxb_runtime}")

    // google cloud secret manager api
    implementation(platform("com.google.cloud:libraries-bom:${Versions.google_cloud_libraries}"))
    implementation("com.google.cloud:google-cloud-secretmanager:${Versions.google_cloud_secretmanager}")

    // test
    testImplementation("io.ktor:ktor-client-mock:${Versions.ktor_version}")
    testImplementation("io.ktor:ktor-server-tests-jvm:${Versions.ktor_version}")
    testImplementation("io.kotest:kotest-assertions-core-jvm:${Versions.kotestVersion}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${Versions.kotlin_version}")
}
