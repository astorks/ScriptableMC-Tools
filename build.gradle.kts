plugins {
    java
    `maven-publish`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "0.14.0" apply false
    id("org.jetbrains.kotlin.jvm") version "1.6.10" apply false
    id("com.github.johnrengelman.shadow") version "7.0.0" apply false
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.1" apply false
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

    dependsOn(":scriptablemc-typescriptgenerator:shadowJar")

    doFirst {
        if(!file("./build").exists()) file("./build").mkdirs()
        if(file("./build/scriptablemc-typescriptgenerator.jar").exists()) file("./build/scriptablemc-typescriptgenerator.jar").delete()
    }

    doLast {
        file("./scriptablemc-typescriptgenerator/build/libs/scriptablemc-typescriptgenerator.jar").copyTo(file("./build/scriptablemc-typescriptgenerator.jar"), overwrite = true)
    }

}
