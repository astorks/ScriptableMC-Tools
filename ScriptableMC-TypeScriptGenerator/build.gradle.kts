plugins {
    java
    `maven-publish`
    id("org.jetbrains.kotlin.jvm")
    id("com.github.johnrengelman.shadow")
    id("org.jetbrains.gradle.plugin.idea-ext")
}

version = "1.0.0"

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
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.31")
    implementation("org.springframework:spring-core:5.3.13")
    implementation("com.beust:klaxon:5.5")
    implementation("com.xenomachina:kotlin-argparser:2.0.7")
    implementation("org.junit.jupiter:junit-jupiter:5.8.2")

    // TODO: Implement tests
    //    testImplementation(kotlin("test"))
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
    archiveFileName.set("scriptablemc-typescriptgenerator.jar")
}

tasks.test {
    useJUnitPlatform()
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
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/astorks/ScriptableMC-Tools")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}