import java.io.ByteArrayOutputStream

plugins {
    `java-library`
    alias(libs.plugins.shadowPlugin)
    alias(libs.plugins.generatePOMPlugin)
}

val getGitHash: String by lazy {
    val stdout = ByteArrayOutputStream()
    rootProject.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = stdout
    }
    stdout.toString().trim()
}

group = "com.clanjhoo"
version = "4.0.2"//.replace("SNAPSHOT", getGitHash)
description = "Framework for spigot that handles creating and accessing databases"

ext.set("projectName", gradle.extra["projectName"].toString())
maven.pom {
    name = gradle.extra["projectName"].toString()
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
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        content {
            includeGroup("org.bukkit")
            includeGroup("org.spigotmc")
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
    // mavenLocal()
}

dependencies {
    // compileOnly(libs.papermc.paperapi)
    compileOnly(libs.spigotmc.spigotapi)
    compileOnly(libs.jetbrains.annotations) {
        isTransitive = false
    }
    implementation(libs.zaxxer.hikariCP) {
        isTransitive = true
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

tasks {
    processResources {
        filesMatching("**/plugin.yml") {
            expand( project.properties )
        }
    }

    shadowJar {
        archiveClassifier.set("all")
        relocate("com.zaxxer.hikari", "com.zaxxer.${rootProject.name.lowercase()}.hikari")
        relocate("org.slf4j", "org.${rootProject.name.lowercase()}.slf4j")
        exclude("META-INF/maven/com.zaxxer/**")
        exclude("META-INF/maven/org.slf4j/**")
    }
}

/*
publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.jar {
    into("META-INF/maven/${project.group}/${project.name}") {
        tasks.findByName("generatePomFileForMavenPublication")?.let { task ->
            from(task)
                rename { it.replace("pom-default.xml", "pom.xml") }
        }
        tasks.findByName("generatePomPropertiesFile")?.let {
            from(it)
        }
    }
}
*/
