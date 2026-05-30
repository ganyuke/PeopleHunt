plugins {
    kotlin("jvm") version "2.4.0-RC"
    id("com.gradleup.shadow") version "9.4.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    jacoco
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

kotlin {
    jvmToolchain(25)
}

val coreCoverageClassDirectories = sourceSets.main.get().output.classesDirs.files.map { classesDir ->
    fileTree(classesDir) {
        include("io/github/ganyuke/peoplehunt/core/**")
        // Sealed event DTOs are covered via bus/service tests; JaCoCo also counts generated accessors.
        exclude("io/github/ganyuke/peoplehunt/core/events/MatchEvent\$*.class")
        exclude("io/github/ganyuke/peoplehunt/core/events/ReportableEvent\$*.class")
        exclude("**/*\$DefaultImpls.class")
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    jacocoTestReport {
        dependsOn(test)
        classDirectories.setFrom(coreCoverageClassDirectories)
        sourceDirectories.setFrom(sourceSets.main.get().kotlin.srcDirs)
        executionData.setFrom(fileTree(layout.buildDirectory) { include("jacoco/test.exec") })
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    jacocoTestCoverageVerification {
        dependsOn(jacocoTestReport)
        classDirectories.setFrom(coreCoverageClassDirectories)
        executionData.setFrom(fileTree(layout.buildDirectory) { include("jacoco/test.exec") })
        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "INSTRUCTION"
                    // Event DTOs excluded above; remaining gap is mostly MatchEngine checkNotNull guards.
                    minimum = "0.90".toBigDecimal()
                }
            }
        }
    }

    check {
        dependsOn(jacocoTestCoverageVerification)
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.1.2")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
