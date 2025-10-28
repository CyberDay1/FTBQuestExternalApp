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
    implementation(project(":ftbq-import"))
    val slf4jVersion: String by project
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    val jacksonVersion: String by project
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
}

repositories {
    mavenCentral()
}


val junitVersion: String by project


dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:${junitVersion}")
    val testfxVersion = "4.0.16-alpha"
    testImplementation("org.testfx:testfx-core:$testfxVersion")
    testImplementation("org.testfx:testfx-junit5:$testfxVersion")
    testRuntimeOnly("org.testfx:openjfx-monocle:21.0.2")
}


tasks.test {
    useJUnitPlatform()
    systemProperty("javafx.headless", "true")
    systemProperty("testfx.headless", "true")
    systemProperty("testfx.robot", "glass")
    systemProperty("glass.platform", "Monocle")
    systemProperty("monocle.platform", "Headless")
    systemProperty("javafx.platform", "Monocle")
    systemProperty("prism.order", "sw")
    systemProperty("prism.text", "t2k")
}

tasks.named<JavaExec>("run") {
    systemProperty("ftbq.editor.forceLaunch", "true")
}
