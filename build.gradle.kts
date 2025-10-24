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

dependencies {
    implementation("org.openjfx:javafx-controls:21.0.2")
    implementation("org.openjfx:javafx-fxml:21.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}
