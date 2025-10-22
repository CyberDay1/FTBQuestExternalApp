import org.gradle.api.tasks.JavaExec

plugins {
    `java-library`
}

dependencies {
    implementation(project(":core-domain"))
    implementation("com.fasterxml.jackson.core:jackson-databind:${rootProject.extra["jacksonVersion"]}")
}

tasks.register<JavaExec>("vanillaBootstrap") {
    group = "ingestion"
    description = "Builds vanilla item catalogs from Minecraft JARs in artifacts/catalogs/input-jars."
    mainClass.set("dev.ftbq.editor.ingest.VanillaCatalogBootstrap")
    classpath = sourceSets["main"].runtimeClasspath
}
