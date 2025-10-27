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
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion") // Add JUnit dependency
}
