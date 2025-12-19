plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("dev.ftbq.editor.HeadlessLauncher")
}

tasks.named<JavaExec>("run") {
    // Ensures the property is set at JVM runtime
    systemProperty("ftbq.editor.forceLaunch", "true")
}

val javafxVersion: String by project
val junitVersion: String by project

dependencies {
    implementation("org.openjfx:javafx-controls:$javafxVersion")
    implementation("org.openjfx:javafx-fxml:$javafxVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("lint") {
    group = "verification"
    description = "Runs repository-wide verification checks."
    dependsOn("check")
}
