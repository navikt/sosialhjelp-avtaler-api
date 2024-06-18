import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.ktlint)
}

ktlint {
    this.version.set(libs.versions.ktlint)
}

group = "no.nav.sosialhjelp"
version = "1.0.0"

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()
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

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.ktor)
    implementation(libs.koin.logging)

    implementation(libs.bundles.logging)

    implementation(libs.bundles.database)

    implementation(libs.token.validation.ktor)
    implementation(libs.token.client.core)

    implementation(libs.signature.api.client.java)
    implementation(libs.jaxb.api)

    implementation(platform(libs.libraries.bom))
    implementation(libs.google.cloud.secretmanager)

    implementation(libs.google.cloud.storage)

    implementation(libs.ktor.client.mock)
    implementation(libs.bundles.apache.poi)

    testImplementation(libs.bundles.test)
}
