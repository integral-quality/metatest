plugins {
    `java-library`
    `maven-publish`
    id("io.freefair.aspectj.post-compile-weaving") version "8.6"
}

group = "io.antigen"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

dependencies {
    // AspectJ — load-time weaving for HTTP interception (core)
    implementation("org.aspectj:aspectjrt:1.9.22")
    api("org.aspectj:aspectjweaver:1.9.22")
    compileOnly("org.aspectj:aspectjtools:1.9.22")

    // HTTP clients (core)
    implementation("io.rest-assured:rest-assured:5.5.6")
    implementation("io.rest-assured:json-path:5.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // JSON/YAML parsing (shared)
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")

    // OpenAPI spec parsing (core)
    implementation("io.swagger.parser.v3:swagger-parser:2.1.22")

    // Math (core)
    api("org.apache.commons:commons-math3:3.6.1")

    // JSON (core)
    implementation("org.json:json:20250107")

    // WireMock — HTTP mocking for tests
    testImplementation("com.github.tomakehurst:wiremock:3.0.1")

    // JUnit platform — needed at compile time for @Test interception and test execution (core)
    compileOnly("org.junit.jupiter:junit-jupiter-api:5.10.0")
    implementation("org.junit.platform:junit-platform-launcher:1.10.0")

    // CLI (ai)
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Gradle plugin API
    compileOnly(gradleApi())

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Xmx2g", "-Xms512m")

    doFirst {
        val runWithAntigen = System.getProperty("runWithAntigen") == "true"
        jvmArgs("-DrunWithAntigen=$runWithAntigen")

        if (runWithAntigen) {
            val agent = configurations.runtimeClasspath.get()
                .find { it.name.contains("aspectjweaver") }?.absolutePath
            if (agent != null) {
                jvmArgs("-javaagent:$agent")
                jvmArgs("-Daj.weaving.verbose=true")
                jvmArgs("-Dorg.aspectj.weaver.showWeaveInfo=true")
                println("[Antigen] Fault simulation enabled — agent: $agent")

                // Verify aop.xml is on the test classpath
                classpath.files.filter { it.isDirectory }.forEach { dir ->
                    val aopXml = dir.resolve("META-INF/aop.xml")
                    if (aopXml.exists()) println("[Antigen] Found aop.xml at: ${aopXml.absolutePath}")
                }
            } else {
                println("[Antigen] WARNING: aspectjweaver not found on classpath")
            }
        }
    }
}
