plugins {
    java
    `maven-publish`
    `java-gradle-plugin`
    id("org.jetbrains.kotlin.jvm") version "1.5.21" apply false
    id("com.github.johnrengelman.shadow") version "7.0.0" apply false
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.0" apply false
}

allprojects {
    group = "com.pixlfox.scriptablemc"

    repositories {
        mavenCentral()
        maven {
            name = "spigotmc"
            url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        }
        maven {
            name = "spigotmc nexus"
            url = uri("https://hub.spigotmc.org/nexus/content/repositories/sonatype-nexus-snapshots/")
        }
        maven {
            name = "minecraft"
            url = uri("https://libraries.minecraft.net")
        }
    }
}

tasks.register("shadowJarAll") {
    group = "shadow"

    dependsOn(":ScriptableMC-TypeScriptGenerator:shadowJar")

    doFirst {
        if(!file("./build").exists()) file("./build").mkdirs()
        if(file("./build/ScriptableMC-TypeScriptGenerator.jar").exists()) file("./build/ScriptableMC-TypeScriptGenerator.jar").delete()
    }

    doLast {
        file("./ScriptableMC-TypeScriptGenerator/build/libs/ScriptableMC-TypeScriptGenerator.jar").copyTo(file("./build/ScriptableMC-TypeScriptGenerator.jar"), overwrite = true)
    }

}
