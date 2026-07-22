
plugins {
    `maven-publish`
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

group = "computer.obscure"
version = "3.1.5"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.znotchill.me/repository/maven-releases/")
}

kotlin {
    jvm()

    macosArm64()
    linuxArm64()
    linuxX64()
    mingwX64()

    jvmToolchain(25)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            implementation("com.google.code.gson:gson:2.13.0")
            implementation("org.jetbrains.kotlin:kotlin-reflect")

            val luauVersion = "1.0.1"
            val luauNativeVersion = "1.0.1-patch2"

            implementation("dev.hollowcube:luau:$luauVersion")
            implementation("com.google.guava:guava:33.2.1-jre")

            val platforms = listOf("windows-x64", "linux-x64", "macos-arm64", "macos-x64")
            platforms.forEach { platform ->
                val dep = "me.znotchill.luau:luau-natives-$platform:$luauNativeVersion"
                implementation(dep)
            }
        }
    }
}

publishing {
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