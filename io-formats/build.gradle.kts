plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

val jacksonVersion: String by project
val slf4jVersion: String by project

dependencies {
    api(project(":core-domain"))
    implementation(project(":ftbq-import"))
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}
