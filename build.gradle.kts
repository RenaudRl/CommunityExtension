plugins {
    kotlin("jvm") version "2.3.20"
    id("com.typewritermc.module-plugin") version "2.1.0"
}
group = "btcrenaud"
version = "0.9"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    implementation("com.typewritermc:BasicExtension:0.9.0")
    implementation("net.dv8tion:JDA:6.5.0")

    // compileOnly only: Shops publishes an event this extension knows how to read, and stays
    // installable on its own. The listener is registered only when the class is on the server.
    compileOnly(project(":TypeWriter-ShopsExtension"))
    testImplementation(kotlin("test"))
    testImplementation("com.google.code.gson:gson:2.13.1")
}

typewriter {
    namespace = "btcrenaud"
    extension {
        name = "Discord"
        shortDescription = "Discord bridge: webhooks, account link, chat sync, bug reports"
        description = "Everything that crosses between the server and Discord, on one reusable destination: declare a webhook once and reference it from anywhere, verify accounts and synchronize ranks, relay chat and console both ways, and post bug reports as embeds or forum threads."
        engineVersion = "0.9.0-beta-175"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA
        paper()
        dependencies {}
    }
}

    

kotlin {
    jvmToolchain(21)
}

