plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "gg.fullwin"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.coroutines.core)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.property)
    testImplementation(libs.coroutines.test)
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("Coro")
                description.set("Kotlin coroutine utilities for cleaner async code")
                url.set("https://github.com/fullwin/coro")
            }
        }
    }

    repositories {
        maven {
            name = "fullwin"
            url = uri(
                if (version.toString().endsWith("SNAPSHOT"))
                    "https://maven.fullwin.gg/snapshots"
                else
                    "https://maven.fullwin.gg/releases"
            )
            credentials {
                username = findProperty("fullwinPublicUsername") as String? ?: System.getenv("FULLWIN_PUBLIC_USERNAME")
                password = findProperty("fullwinPublicPassword") as String? ?: System.getenv("FULLWIN_PUBLIC_PASSWORD")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
