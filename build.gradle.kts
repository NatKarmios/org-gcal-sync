import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.4.32"
}

group = "com.karmios.code"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    compile("com.orgzly:org-java:1.2.2")
    compile("com.github.caldav4j:caldav4j:0.9.2")
    compile("com.google.api-client:google-api-client:1.23.0")
    compile("com.google.oauth-client:google-oauth-client-jetty:1.23.0")
    compile("com.google.apis:google-api-services-calendar:v3-rev305-1.23.0")
    compile("com.sksamuel.hoplite:hoplite-core:1.4.3")
    compile("com.sksamuel.hoplite:hoplite-yaml:1.4.3")
    compile("org.jetbrains.kotlinx:kotlinx-cli:0.3.2")
    compile("org.apache.logging.log4j:log4j-api:2.14.1")
    compile("org.apache.logging.log4j:log4j-core:2.14.1")
    compile("org.apache.logging.log4j:log4j-slf4j-impl:2.14.1")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    languageVersion = "1.5"
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}