import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.5.21"
    application
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "com.karmios.code"
version = "1.0.2"

tasks.named<ShadowJar>("shadowJar") {
    outputs.upToDateWhen { false }

    group = "Build"
    description = "Creates a fat jar"
    archiveFileName.set("org-gcal-sync_${project.version}.jar")

    from(sourceSets.main.map { it.output })
    from(project.configurations.runtimeClasspath)

    exclude("**/*.kotlin_metadata")
    exclude("**/*.kotlin_module")
    exclude("META-INF/maven/**")
}

application {
    mainClass.set("com.karmios.code.orggcalsync.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    with(Versions) {
        implementation(kotlin("stdlib"))
        implementation("com.orgzly:org-java:$orgzly")
        implementation("com.google.api-client:google-api-client:$google_api")
        implementation("com.google.oauth-client:google-oauth-client-jetty:$google_api")
        implementation("com.google.apis:google-api-services-calendar:v3-rev305-$google_api")
        implementation("com.sksamuel.hoplite:hoplite-core:$hoplite")
        implementation("com.sksamuel.hoplite:hoplite-yaml:$hoplite")
        implementation("org.jetbrains.kotlinx:kotlinx-cli:$kotlinx_cli")
        implementation("org.apache.logging.log4j:log4j-api:$log4j")
        implementation("org.apache.logging.log4j:log4j-core:$log4j")
        implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4j")
    }
}

object Versions {
    const val google_api  = "1.23.0"
    const val hoplite     = "1.4.3"
    const val kotlinx_cli = "0.3.2"
    const val log4j       = "2.14.1"
    const val orgzly      = "1.2.2"
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    languageVersion = "1.5"
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}
