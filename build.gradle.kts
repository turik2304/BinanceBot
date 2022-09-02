import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.0"
    application
}

group = "me.artursibagatullin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClassName = "MainKt"
}

dependencies {
    implementation("io.github.binance:binance-connector-java:1.8.0")
    implementation("com.google.code.gson:gson:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

}