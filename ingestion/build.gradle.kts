plugins {
    `java-library`
}

repositories {
    mavenCentral()  // Add this to resolve external dependencies
}

val sqliteVersion: String by project
val junitVersion: String by project // Add JUnit version if not already in gradle.properties

dependencies {
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion") // Add JUnit dependency for tests
}

tasks.test {
    useJUnitPlatform() // Ensure tests use JUnit 5
}
