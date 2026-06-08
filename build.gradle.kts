import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.version.catalog.update)
}

group = "no.nav.sosialhjelp"
version = "1.0.0"

buildscript {
    configurations.classpath {
        resolutionStrategy.force("org.codehaus.plexus:plexus-utils:4.0.3")
    }
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()
    isZip64 = true
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

application {
    mainClass.set("no.nav.sosialhjelp.avtaler.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {

    implementation(libs.konfig)

    implementation(libs.bundles.ktor.server)

    implementation(libs.bundles.ktor.client)

    implementation(libs.bundles.serialization)

    constraints {
        implementation("io.netty:netty-handler:4.2.15.Final") {
            because("Temporary override for GHSA-3qp7-7mw8-wx86/CVE-2026-44249 until upstream transitive dependencies are updated")
        }
    }

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.ktor)
    implementation(libs.koin.logging)

    implementation(libs.bundles.logging)

    implementation(libs.bundles.database)

    implementation(libs.token.validation.ktor)
    implementation(libs.token.client.core)

    implementation(libs.signature.api.client.java)
    implementation("org.apache.httpcomponents.client5:httpclient5:5.6.1")
    implementation(libs.jaxb.api)

    implementation(platform(libs.libraries.bom))
    implementation(libs.google.cloud.secretmanager)

    implementation(libs.google.cloud.storage)

    implementation(libs.ktor.client.mock)
    implementation(libs.bundles.apache.poi)
    implementation(libs.opentelemetry.instrumentation.annotations)
    implementation(libs.micrometer.registry.prometheus)

    testImplementation(libs.bundles.test)
}
