plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.8.0")
}

gradle.extra["projectName"] = "DBHandler"
rootProject.name = gradle.extra["projectName"].toString().lowercase()
