plugins {
    id("java")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation(project(":io-formats"))
    testImplementation(project(":ingestion"))
}

tasks.test {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
}

