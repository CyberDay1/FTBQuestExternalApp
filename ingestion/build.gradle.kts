plugins {
    `java-library`
}

dependencies {
    implementation(project(":core-domain"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
}
