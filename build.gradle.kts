plugins {
    kotlin("jvm") version "2.3.20"
    id("com.typewritermc.module-plugin") version "2.1.0"
}
group = "btcrenaud"
version = "0.0.8"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    implementation("com.typewritermc:BasicExtension:0.9.0")
    implementation("net.dv8tion:JDA:6.5.0")
    testImplementation(kotlin("test"))
}

typewriter {
    namespace = "btcrenaud"
    extension {
        name = "Community"
        shortDescription = "Community system for TypeWriter"
        description = "Community extension providing community management features for TypeWriter, including team building and community engagement tools."
        engineVersion = "0.9.0-beta-175"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA
        paper()
        dependencies {}
    }
}

    

kotlin {
    jvmToolchain(21)
}

