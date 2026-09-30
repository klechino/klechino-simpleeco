plugins {
    java
}

group = "com.arcticsus"
version = "1.0.1"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
