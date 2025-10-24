plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

application {
    mainClass.set("dev.ftbq.editor.HeadlessLauncher")
}

val javafxVersion = project.findProperty("javafxVersion") as String

javafx {
    version = javafxVersion
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

dependencies {
    implementation(project(":datastore"))
    implementation(project(":core-domain"))
    implementation(project(":services"))
    implementation(project(":ingestion"))
    val slf4jVersion: String by project
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    val jacksonVersion: String by project
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
}
