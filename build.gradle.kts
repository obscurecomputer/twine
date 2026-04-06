plugins {
    `maven-publish`
    kotlin("jvm") version "2.3.0"
}

group = "computer.obscure"
version = "3.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val luauJavaVersion = "1.0.1-debug"
dependencies {
    testImplementation(kotlin("test"))
    implementation("com.google.code.gson:gson:2.13.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("dev.hollowcube:luau:1.0.1")
    implementation("dev.hollowcube:luau-natives-windows-x64:${luauJavaVersion}")
    implementation("dev.hollowcube:luau-natives-linux-x64:${luauJavaVersion}")
    implementation("dev.hollowcube:luau-natives-macos-x64:1.0.0-debug")
    implementation("dev.hollowcube:luau-natives-macos-arm64:${luauJavaVersion}")
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
