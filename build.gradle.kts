repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    implementation("net.dv8tion:JDA:5.0.0-beta.24") // Use a recent version

}

plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin")
}

group = "btc.renaud"
version = "0.0.1"

typewriter {
    namespace = "renaud"

    extension {
        name = "Community"
        shortDescription = "Unified community tools: Discord Link & Bug Reports"
        description = """
            CommunityExtension centralizes player engagement tools:
            - Discord Link: Sync ranks and verify accounts.
            - Bug Reports: Customizable reporting menus with Dialog integration.
        """.trimIndent()
        engineVersion = file("../../version.txt").readText().trim()
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA

        dependencies {
            paper()
        }
    }
}

kotlin {
    jvmToolchain(21)
}
