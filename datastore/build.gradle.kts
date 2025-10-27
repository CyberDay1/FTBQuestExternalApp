plugins {
    `java-library`
}

repositories {
    mavenCentral()  // Ensure repositories are defined to resolve dependencies
}

val sqliteVersion: String by project
val junitVersion: String by project // Add junit version if not already in gradle.properties

dependencies {
    implementation(project(":core-domain"))
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")
    val jacksonVersion: String by project
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion") // Add JUnit dependency
}
