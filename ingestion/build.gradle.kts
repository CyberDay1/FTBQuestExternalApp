import org.gradle.api.tasks.JavaExec

plugins {
    `java-library`
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
}

tasks.register<JavaExec>("vanillaBootstrap") {
    group = "ingestion"
    description = "Builds vanilla item catalogs from Minecraft JARs in artifacts/catalogs/input-jars."
    mainClass.set("dev.ftbq.editor.ingest.VanillaCatalogBootstrap")
    classpath = sourceSets["main"].runtimeClasspath
}
