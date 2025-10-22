plugins {
    application
    id("org.openjfx.javafxplugin")
}

application {
    mainClass.set("com.ftbquests.app.Main")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls")
}
