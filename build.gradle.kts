
object Versions {
    const val konfig_version = "1.6.10.0"
    const val ktor_version = "2.2.3"
    const val kotlin_version = "1.8.10"
    const val kotlin_serialization_plugin_version = "1.5.0"
    const val kotestVersion = "5.5.5"
    const val ktlint = "0.45.2"
    const val logback_version = "1.4.5"
    const val logback_encoder_version = "7.2"
    const val jackson_version = "2.14.2"
    const val microutils_version = "3.0.5"
    const val token_support_version = "3.0.3"
    const val kotliquery_version = "1.9.0"
    const val flywaydb_version = "9.7.0"
    const val hikari_version = "5.0.1"
    const val postgresql_version = "42.5.3"
    const val digipost_signature_api_client = "7.0-RC7"
    const val jaxb_runtime = "2.3.7"
    const val google_cloud_libraries = "26.14.0"
    const val google_cloud_secretmanager = "2.10.0"

    // constraints
    const val json_smart = "2.4.9"
    const val netty = "4.1.94.Final"
    const val guava = "32.0.1-jre"
}

plugins {
    application
    kotlin("jvm") version "1.9.10"
    kotlin("plugin.serialization") version "1.9.21"
    id("io.ktor.plugin") version "2.3.4"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
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

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlin_serialization_plugin_version}")
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

    // google cloud storage
    implementation("com.google.cloud:google-cloud-storage")

    // test
    testImplementation("io.ktor:ktor-client-mock:${Versions.ktor_version}")
    testImplementation("io.ktor:ktor-server-tests-jvm:${Versions.ktor_version}")
    testImplementation("io.kotest:kotest-assertions-core-jvm:${Versions.kotestVersion}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${Versions.kotlin_version}")

    constraints {
        implementation("net.minidev:json-smart:${Versions.json_smart}") {
            because("https://github.com/advisories/GHSA-493p-pfq6-5258")
        }
        implementation("io.netty:netty-handler:${Versions.netty}") {
            because("https://github.com/advisories/GHSA-6mjq-h674-j845")
        }
        implementation("com.google.guava:guava:${Versions.guava}") {
            because("https://github.com/advisories/GHSA-7g45-4rm6-3mm3")
        }
    }
}
