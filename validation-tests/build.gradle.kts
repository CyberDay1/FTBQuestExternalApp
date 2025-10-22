plugins {
    `java-library`
}

val junitVersion: String by project

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
}

tasks.test {
    useJUnitPlatform()
}
