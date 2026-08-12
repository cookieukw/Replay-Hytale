import groovy.json.JsonSlurper
import java.util.Properties
import java.io.FileInputStream

val modManifest = JsonSlurper().parse(file("src/main/resources/manifest.json")) as Map<*, *>

version = modManifest["Version"] as String
group = "gg.alexandre"

val localProps = Properties()
val localPropsFile = project.rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}

val hytaleModsDir = localProps.getProperty("hytale.mods.dir")
val hytaleHome = localProps.getProperty("hytale.dir")
    ?: if (System.getProperty("os.name").lowercase().contains("win")) {
        "${System.getProperty("user.home")}/AppData/Roaming/Hytale"
    } else {
        "${System.getProperty("user.home")}/.var/app/com.hypixel.HytaleLauncher/data/Hytale"
    }

val targetModsDir = hytaleModsDir ?: "$hytaleHome/UserData/Mods"

tasks.register<Copy>("deploy") {
    dependsOn("jar")
    from(tasks.jar.get().archiveFile)
    into(targetModsDir)

    doFirst {
        if (!file(targetModsDir).exists()) {
            println("WARNING: Target mods folder not found at: $targetModsDir")
            println("Configure 'hytale.mods.dir=/correct/path' in your local.properties")
        }
    }
}

tasks.jar {
    var serverVersion = modManifest["ServerVersion"] as String;
    if (serverVersion.contains("=")) {
        serverVersion = serverVersion.split("=")[1].trim()
    }

    archiveFileName.set("${modManifest["Name"]}-${modManifest["Version"]}-Hytale-${serverVersion}.jar")
    finalizedBy("deploy")
}