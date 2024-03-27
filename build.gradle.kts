
buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

plugins {
    `java-library`
    `maven-publish`
    signing
    id("org.jetbrains.kotlin.jvm") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.10"
    id("org.jlleitschuh.gradle.ktlint") version "10.0.0"
    id("com.github.ben-manes.versions") version "0.42.0"
}

version = project.property("version") as String

java {
    withSourcesJar()
//    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_11
}

val aar by configurations.creating

val copyAars by tasks.registering(Copy::class) {
    mkdir("build/aar")
    from(configurations["aar"]) {
        include("*.aar")
    }
    into("build/aar")
}

val extractClasses by tasks.creating {
    dependsOn(copyAars)
    val aarFileTree = fileTree("build/aar")

    aarFileTree.forEach { aarFile: File ->

        dependsOn(
            tasks.create(aarFile.name, Copy::class) {
                from(zipTree(aarFile))
                include("classes.jar")
                rename("classes.jar", aarFile.nameWithoutExtension + ".jar")
                into("build/jar")
            }
        )
    }
}

tasks {
    compileKotlin {
        dependsOn(extractClasses)
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
        }
    }
    withType<Sign>().configureEach {
        onlyIf { !project.gradle.startParameter.taskNames.any { "MavenLocal" in it } }
    }
}

val jar by tasks.getting(Jar::class) {
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter {
            it.path.startsWith("${projectDir.path}${File.separator}build${File.separator}jar")
        }.map { zipTree(it) }
    })
}

//group = "customfirebaseforkforjava"

val sourceSets = project.the<SourceSetContainer>()

val cleanLibs by tasks.creating(Delete::class) {
    delete("$buildDir/libs")
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/uwtBomman/firebase-java-sdk")
            credentials {
                username = "uwtBomman"
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
}

dependencies {
    compileOnly("org.robolectric:android-all:12.1-robolectric-8229987")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.6.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.0")
    // firebase aars
    aar("com.google.firebase:firebase-firestore:24.10.0")
    aar("com.google.firebase:firebase-functions:20.4.0")
    aar("com.google.firebase:firebase-database:20.3.0")
    aar("com.google.firebase:firebase-config:21.6.0")
    aar("com.google.firebase:firebase-installations:17.2.0")
    // extracted aar dependencies
    api(fileTree(mapOf("dir" to "build/jar", "include" to listOf("*.jar"))))
    // polyfill dependencies
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    // firebase dependencies
    implementation("javax.inject:javax.inject:1")
    implementation("com.squareup.okhttp3:okhttp:3.12.13")
    implementation("android.arch.lifecycle:common:1.1.1")
    implementation("io.grpc:grpc-protobuf-lite:1.52.1")
    implementation("io.grpc:grpc-stub:1.52.1")
    implementation("androidx.collection:collection:1.2.0")
    implementation("androidx.lifecycle:lifecycle-common:2.4.0")
    implementation("io.grpc:grpc-okhttp:1.52.1")
}

tasks.named("publishToMavenLocal").configure {
    dependsOn(cleanLibs, jar)
}

tasks.named("publish").configure {
    dependsOn(cleanLibs)
}

ktlint {
    version.set("0.41.0")
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications)
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {

    fun isNonStable(version: String): Boolean {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
        val versionMatch = "^[0-9,.v-]+(-r)?$".toRegex().matches(version)

        return (stableKeyword || versionMatch).not()
    }

    rejectVersionIf {
        isNonStable(candidate.version)
    }

    checkForGradleUpdate = true
    outputFormatter = "plain,html"
    outputDir = "build/dependency-reports"
    reportfileName = "dependency-updates"
}
