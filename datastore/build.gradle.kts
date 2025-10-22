plugins {
    `java-library`
}

val sqliteVersion: String by project

dependencies {
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")
}
