plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

allprojects {
    group = "computer.obscure.twine"
    version = "3.1.8"

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.znotchill.me/releases/")
    }
}

subprojects {
    apply(plugin = "maven-publish")

    configure<PublishingExtension> {
        repositories {
            maven {
                name = "obscurerepo"
                url = uri("https://repo.obscure.computer/repository/maven-releases/")
                credentials {
                    username = findProperty("obscureUsername") as String? ?: System.getenv("OBSCURE_MAVEN_USER")
                    password = findProperty("obscurePassword") as String? ?: System.getenv("OBSCURE_MAVEN_PASS")
                }
            }
            mavenLocal()
        }
    }
}