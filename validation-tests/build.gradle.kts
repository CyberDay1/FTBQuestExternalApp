plugins {
    `java-library`
}

val junitVersion: String by project

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation(project(":io-formats"))
}

tasks.test {
    useJUnitPlatform()
}
