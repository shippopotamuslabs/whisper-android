import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.publish.maven.MavenPublication

plugins {
    // AGP 9 provides built-in Kotlin support; do not apply org.jetbrains.kotlin.android.
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.shippopotamus.whisper"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 34

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                arguments("-DGGML_OPENMP=OFF", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val hasWhisperCpp = file("../third_party/whisper.cpp/src/whisper.cpp").exists()
    if (hasWhisperCpp) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    } else {
        println("Skipping whisper externalNativeBuild: third_party/whisper.cpp not found.")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.shippopotamuslabs"
            artifactId = "whisper-android"
            version = findProperty("VERSION_NAME") as String

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("whisper-android")
                description.set("On-device speech-to-text for Android via whisper.cpp (JNI + CMake).")
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
