plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

val sqliteVersion: String by project
val jacksonVersion: String by project
val slf4jVersion: String by project
val junitVersion: String by project

dependencies {
    implementation(project(":core-domain"))
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("commons-io:commons-io:2.15.1")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
}

tasks.test {
    useJUnitPlatform()
}
