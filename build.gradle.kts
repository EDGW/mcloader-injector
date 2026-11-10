plugins { base }

version = "0.1.0"

subprojects {
    version = rootProject.version
    apply(plugin = "java-library")
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(8)
        options.encoding = "UTF-8"
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    tasks.withType<Jar>().configureEach {
        manifest.attributes("Implementation-Version" to project.version.toString())
    }
    dependencies { "testImplementation"("junit:junit:4.13.2") }
}

tasks.build { dependsOn(":bridge:build", ":agent:build") }
tasks.check { dependsOn(":bridge:check", ":agent:check") }
