import java.io.ByteArrayOutputStream

plugins {
    java
    `maven-publish`
    alias(libs.plugins.shadowPlugin)
}

group = "com.clanjhoo"
version = "3.0.1"
description = "DB Framework for bukkit plugins"

val getGitHash: String by lazy {
    val stdout = ByteArrayOutputStream()
    rootProject.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = stdout
    }
    stdout.toString().trim()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.ORACLE
    }
}

repositories {
    gradlePluginPortal {
        content {
            includeGroup("com.gradleup")
        }
    }
    maven {
        url = uri("https://papermc.io/repo/repository/maven-public/")
        content {
            includeGroup("io.papermc.paper")
        }
    }
    maven {
        url = uri("https://repo.aikar.co/content/groups/aikar/")
        content {
            includeGroup("net.md-5")
        }
    }
    mavenCentral()
    mavenLocal()
}

dependencies {
    compileOnly(libs.papermc.paperAPI)
    implementation(libs.zaxxer.hikariCP) {
        isTransitive = false
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}

tasks {
    processResources {
        filesMatching("**/plugin.yml") {
            expand( project.properties )
        }
    }

    shadowJar {
        archiveFileName.set("${rootProject.name}-${version}.jar".replace("SNAPSHOT", getGitHash))
        relocate("com.zaxxer.hikari", "com.zaxxer.${rootProject.name.lowercase()}.hikari")
    }
}
