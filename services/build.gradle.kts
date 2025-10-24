plugins {
    `java-library`
}

val slf4jVersion: String by project
val jacksonVersion: String by project
val junitVersion: String by project

dependencies {
    api(project(":core-domain"))
    api(project(":io-formats"))
    implementation(project(":datastore"))
    implementation(project(":ingestion"))
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
}

tasks.test {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
}


dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
}

