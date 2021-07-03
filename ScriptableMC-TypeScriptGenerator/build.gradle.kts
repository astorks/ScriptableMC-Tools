plugins {
    java
    id("org.jetbrains.kotlin.jvm")
    id("com.github.johnrengelman.shadow")
    id("org.jetbrains.gradle.plugin.idea-ext")

}

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
    implementation("com.xenomachina:kotlin-argparser:2.0.7")
    implementation("org.junit.jupiter:junit-jupiter:5.7.0")

    testImplementation(kotlin("test"))
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
    archiveFileName.set("ScriptableMC-TypeScriptGenerator.jar")
}

tasks.test {
    useJUnitPlatform()
}