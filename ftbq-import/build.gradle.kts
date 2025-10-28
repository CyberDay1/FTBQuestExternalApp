plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

val junitVersion: String by project

dependencies {
    api(project(":core-domain"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}
