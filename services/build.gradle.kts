plugins {
    `java-library`
}

val slf4jVersion: String by project

dependencies {
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")
}
