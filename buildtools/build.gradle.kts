plugins {
    java
    `maven-publish`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
    id("org.jetbrains.kotlin.jvm")
    id("com.github.johnrengelman.shadow")
    id("org.jetbrains.gradle.plugin.idea-ext")
}

version = "1.0.1-SNAPSHOT"
group = "com.pixlfox.scriptablemc"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility  = JavaVersion.VERSION_17
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("org.springframework:spring-core:5.3.15")
    implementation("com.beust:klaxon:5.5")
    implementation(gradleApi())
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = "17"
    kotlinOptions.javaParameters = true
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks.compileTestKotlin {
    kotlinOptions.jvmTarget = "17"
    kotlinOptions.javaParameters = true
}

tasks.shadowJar {
    archiveFileName.set("scriptablemc-buildtools.jar")
}

pluginBundle {
    website = "https://github.com/astorks/ScriptableMC-Tools"
    vcsUrl = "https://github.com/astorks/ScriptableMC-Tools"
    tags = listOf("scriptablemc")
}

gradlePlugin {
    plugins {
        create("tsgenerator") {
            id = "com.pixlfox.scriptablemc.buildtools"
            displayName = "ScriptableMC BuildTools"
            description = "Build tools for ScriptableMC"
            implementationClass = "com.pixlfox.scriptablemc.BuildToolsPlugin"
        }
    }
}

publishing {
    publications {
        shadow {
            create<MavenPublication>("maven") {
                artifact(tasks["shadowJar"])
            }
        }
    }
    repositories {
        maven {
            name = "LocalMavenFS"
            url = uri("../maven-repo")
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/astorks/ScriptableMC-Tools")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}