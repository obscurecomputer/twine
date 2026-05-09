plugins {
    `maven-publish`
    kotlin("jvm") version "2.3.0"
}

group = "computer.obscure"
version = "3.1.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.znotchill.me/repository/maven-releases/")
}

dependencies {
    testImplementation(kotlin("test"))
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

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(25)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            group
            artifactId = "twine"
            version
        }
    }

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
