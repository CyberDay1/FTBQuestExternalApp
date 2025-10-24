plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

val jacksonVersion: String by project
val sqliteVersion: String by project


dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")
}
