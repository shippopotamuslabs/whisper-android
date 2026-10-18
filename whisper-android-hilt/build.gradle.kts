import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    `maven-publish`
}

android {
    namespace = "com.shippopotamus.whisper.hilt"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":whisper-android"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.shippopotamuslabs"
            artifactId = "whisper-android-hilt"
            version = findProperty("VERSION_NAME") as String

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("whisper-android-hilt")
                description.set("Optional Hilt bindings for whisper-android (provides WhisperEngine).")
                url.set("https://gitlab.com/browntown.development-group/whisper-android")
                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("browntown.development")
                        name.set("Browntown Development")
                    }
                }
                scm {
                    connection.set("scm:git:git://gitlab.com/browntown.development-group/whisper-android.git")
                    developerConnection.set("scm:git:ssh://git@gitlab.com/browntown.development-group/whisper-android.git")
                    url.set("https://gitlab.com/browntown.development-group/whisper-android")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitLab"
            url = uri("https://gitlab.com/api/v4/projects/87770779/packages/maven")
            credentials(HttpHeaderCredentials::class) {
                name = "Job-Token"
                value = System.getenv("CI_JOB_TOKEN").orEmpty()
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}
