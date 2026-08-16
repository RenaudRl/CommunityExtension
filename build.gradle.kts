plugins {
    kotlin("jvm") version "2.3.20"
    id("com.typewritermc.module-plugin") version "2.1.0"
    id("com.gradleup.shadow") version "9.4.1"
}
group = "btcrenaud"
version = "0.10"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    implementation("com.typewritermc:BasicExtension:0.9.0")
    implementation("net.dv8tion:JDA:6.5.0")

    testImplementation(kotlin("test"))
    testImplementation("com.google.code.gson:gson:2.13.1")
}

typewriter {
    namespace = "btcrenaud"
    extension {
        name = "Discord"
        shortDescription = "Discord bridge: webhooks, fact events, account link, chat sync, bug reports"
        description = "Everything that crosses between the server and Discord, on one reusable destination: declare a webhook once and reference it from anywhere, publish Typewriter fact changes by player, group or audience, verify accounts and synchronize ranks, relay chat and console both ways, and post bug reports as embeds or forum threads."
        engineVersion = "0.9.0-beta-175"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA
        paper()
        dependencies {}
    }
}

    

kotlin {
    jvmToolchain(21)
}

tasks.shadowJar {
    archiveClassifier.set("")
    dependencies {
        // Typewriter already provides BasicExtension; only JDA and its runtime dependencies
        // belong in this standalone extension artifact.
        exclude(dependency("com.typewritermc:BasicExtension:.*"))
    }
    exclude("kotlin/**")
    exclude("org/intellij/**")
    exclude("org/jetbrains/**")
    exclude("META-INF/maven/**")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

