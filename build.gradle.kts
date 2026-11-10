plugins { base }

subprojects {
    apply(plugin = "java-library")
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(8)
        options.encoding = "UTF-8"
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    dependencies { "testImplementation"("junit:junit:4.13.2") }
}

tasks.build { dependsOn(":bridge:build", ":agent:build") }
tasks.check { dependsOn(":bridge:check", ":agent:check") }
