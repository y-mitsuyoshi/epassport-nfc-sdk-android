plugins {
    kotlin("jvm")
    application
}

group = "com.example.epassport"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.example.epassport.server.ServerApplicationKt")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:3.2.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.77")

    // AWS KMS
    implementation("software.amazon.awssdk:kms:2.25.0")

    // GCP Cloud KMS
    implementation("com.google.cloud:google-cloud-kms:2.50.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
