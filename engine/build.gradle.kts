plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    `maven-publish`
}

kotlin {
    jvmToolchain(25)
}

val luauVersion = "1.2.1"
val luauNativeVersion = "1.2.1"

dependencies {
    testImplementation(kotlin("test"))
    api(project(":core"))

    implementation(libs.kotlinx.serialization.json)
    implementation("com.google.code.gson:gson:2.13.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.google.guava:guava:33.2.1-jre")

    compileOnly("dev.hollowcube:luau:$luauVersion")
    testImplementation("dev.hollowcube:luau:$luauVersion")

    listOf("windows-x64", "linux-x64", "macos-arm64", "macos-x64").forEach { platform ->
        val natives = "dev.hollowcube:luau-natives-$platform:$luauNativeVersion"
        compileOnly(natives)
        testRuntimeOnly(natives)
    }
}
tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}