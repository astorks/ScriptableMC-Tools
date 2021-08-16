plugins {
    java
    `maven-publish`
    `java-gradle-plugin`
    id("org.jetbrains.kotlin.jvm")
    id("com.github.johnrengelman.shadow")
    id("org.jetbrains.gradle.plugin.idea-ext")
}

version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility  = JavaVersion.VERSION_11
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
    implementation("org.springframework:spring-core:5.3.8")
    implementation("com.beust:klaxon:5.5")
    implementation(project(":ScriptableMC-TypeScriptGenerator"))
    implementation(gradleApi())
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = "11"
    kotlinOptions.javaParameters = true
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks.compileTestKotlin {
    kotlinOptions.jvmTarget = "11"
    kotlinOptions.javaParameters = true
}

tasks.shadowJar {
    archiveFileName.set("ScriptableMC-TypeScriptGenerator-Gradle.jar")
}

gradlePlugin {
    plugins {
        create("hello") {
            id = "com.pixlfox.gradle.tsgenerator"
            implementationClass = "com.pixlfox.gradle.TypeScriptDefinitionGeneratorPlugin"
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
            url = uri("D:\\Repos\\maven\\")
        }
    }
}