import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.5.21"
    application
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "com.karmios.code"
version = "1.0.6"

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

    transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer::class.java)
}

application {
    mainClass.set("com.karmios.code.orggcalsync.OrgGcalSync")
}

repositories {
    mavenCentral()
}

dependencies {
    with(Versions) {
        implementation(kotlin("stdlib"))
        implementation("com.orgzly:org-java:$orgzly")
        implementation("com.google.api-client:google-api-client:2.0.0")
        implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
        implementation("com.google.apis:google-api-services-calendar:v3-rev20220715-2.0.0")
        implementation("com.sksamuel.hoplite:hoplite-core:$hoplite")
        implementation("com.sksamuel.hoplite:hoplite-yaml:$hoplite")
        implementation("commons-validator:commons-validator:$apacheCommonsValidator")
        implementation("org.jetbrains.kotlinx:kotlinx-cli:$kotlinx_cli")
        implementation("org.apache.logging.log4j:log4j-api:$log4j")
        implementation("org.apache.logging.log4j:log4j-core:$log4j")
        implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4j")
        implementation("com.github.kittinunf.fuel:fuel:$fuel")
        implementation("com.beust:klaxon:$klaxon")
        implementation("com.amazonaws:aws-lambda-java-core:$awsLambdaCore")
        implementation("com.amazonaws:aws-lambda-java-events:$awsLambdaEvents")
        runtimeOnly("com.amazonaws:aws-lambda-java-log4j2:$awsLambdaLog4j")
    }
}

object Versions {
    const val hoplite         = "1.4.3"
    const val kotlinx_cli     = "0.3.2"
    const val log4j           = "2.14.1"
    const val orgzly          = "1.2.2"
    const val fuel            = "2.3.1"
    const val klaxon          = "5.5"
    const val awsLambdaCore   = "1.2.1"
    const val awsLambdaEvents = "3.11.0"
    const val awsLambdaLog4j  = "1.5.1"
    const val apacheCommonsValidator = "1.7"
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    languageVersion = "1.5"
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}
