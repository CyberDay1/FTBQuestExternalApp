plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

application {
    mainClass.set("dev.ftbq.editor.MainApp")
}

val javafxVersion = project.findProperty("javafxVersion") as String

javafx {
    version = javafxVersion
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

dependencies {
    implementation(project(":datastore"))
    implementation(project(":core-domain"))
}
