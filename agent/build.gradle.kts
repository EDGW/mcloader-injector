plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.6.1"
}

dependencies {
    compileOnly(project(":bridge"))
    testImplementation(project(":bridge"))
    implementation("org.ow2.asm:asm:9.11.1")
}

val bridgeJar = project(":bridge").tasks.named<Jar>("jar")
tasks.processResources {
    from(bridgeJar) {
        into("bootstrap")
        rename { "bridge.jar" }
    }
}

tasks.jar {
    manifest.attributes("Premain-Class" to "com.kako.chan.mcloader.injector.agent.InstallerAgent")
}
tasks.shadowJar {
    archiveFileName.set("mcloader-injector-agent.jar")
    relocate("org.objectweb.asm", "com.kako.chan.mcloader.injector.internal.asm")
}
tasks.assemble { dependsOn(tasks.shadowJar) }
tasks.test {
    dependsOn(tasks.shadowJar)
    systemProperty("injector.jar", tasks.shadowJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("fixture.classes", sourceSets.test.get().output.classesDirs.asPath)
    systemProperty("fixture.java", System.getenv("INJECTOR_TEST_JAVA") ?: "${System.getProperty("java.home")}/bin/java")
}
