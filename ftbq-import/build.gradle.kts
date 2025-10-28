plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":core-domain"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
