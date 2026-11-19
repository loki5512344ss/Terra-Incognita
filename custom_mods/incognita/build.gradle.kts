plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.148"
    idea
    pmd
    id("com.github.spotbugs") version "6.1.7"
}

val modId = property("mod_id") as String
val modName = property("mod_name") as String
val modVersion = property("mod_version") as String
val modGroupId = property("mod_group_id") as String
val modLicense = property("mod_license") as String
val modAuthors = property("mod_authors") as String
val modDescription = property("mod_description") as String
val minecraftVersion = property("minecraft_version") as String
val minecraftVersionRange = property("minecraft_version_range") as String
val neoVersion = property("neo_version") as String
val neoVersionRange = property("neo_version_range") as String
val loaderVersionRange = property("loader_version_range") as String
val javaVersion = property("java_version") as String

version = modVersion
group = modGroupId

base {
    archivesName.set(modId)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))
    withSourcesJar()
}

neoForge {
    version = neoVersion

    runs {
        register("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        // Scenario runner: ./gradlew runClientTest -> screenshots in run/clientTest/screenshots/ct-*.png
        register("clientTest") {
            client()
            gameDirectory.set(file("run/clientTest"))
            // ./gradlew runClientTest -PctScenario=weather picks the scenario; the default runs the GUI checks.
            systemProperty("incognita.clientTest", providers.gradleProperty("ctScenario").orElse("1").get())
        }
        register("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        register("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    unitTest {
        enable()
        testedMod = mods.getByName(modId)
    }
}

repositories {
    mavenCentral()
    maven("https://maven.maxhenkel.de/repository/public") // Simple Voice Chat API
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

// Sodium is compile-only (the lighting mixin targets its mesher). The published file is a jar-in-jar wrapper, so the
// real classes (and the Fabric rendering API jars it bundles) are extracted from META-INF/jarjar.
val sodiumWrapper: Configuration = configurations.create("sodiumWrapper") { isTransitive = false }
val extractSodium = tasks.register<Sync>("extractSodium") {
    from(zipTree(sodiumWrapper.elements.map { it.first().asFile })) {
        include("META-INF/jarjar/*.jar")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("sodium-libs"))
}

dependencies {
    sodiumWrapper("maven.modrinth:sodium:mc1.21.1-0.8.13-neoforge")
    compileOnly(fileTree(layout.buildDirectory.dir("sodium-libs")) { include("*.jar") }.builtBy(extractSodium))
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.27")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0") {
        exclude(group = "org.slf4j") // NeoForge pins slf4j strictly; ArchUnit only needs the API that is already there
    }
}

// Static analysis. Reports land in build/reports/{pmd,spotbugs}; `./gradlew check` runs them with the tests.
pmd {
    toolVersion = "7.8.0"
    ruleSets = emptyList()
    ruleSetFiles = files("config/pmd/ruleset.xml")
    isConsoleOutput = true
    isIgnoreFailures = false
}

tasks.pmdTest {
    enabled = false // tests repeat literals and assert freely on purpose
}

tasks.pmdMain {
    // Mixin classes are abstract with private injector methods that only Mixin calls; PMD reads them as defects.
    exclude("**/mixin/**")
}

spotbugs {
    toolVersion = "4.8.6"
    excludeFilter.set(file("config/spotbugs/exclude.xml"))
    ignoreFailures.set(false)
}
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required.set(true) }
    reports.create("xml") { required.set(true) }
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "neo_version" to neoVersion,
        "neo_version_range" to neoVersionRange,
        "loader_version_range" to loaderVersionRange,
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_license" to modLicense,
        "mod_version" to modVersion,
        "mod_authors" to modAuthors,
        "mod_description" to modDescription,
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}

sourceSets.main {
    resources.srcDir(generateModMetadata)
}
neoForge.ideSyncTask(generateModMetadata)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion.toInt())
}

tasks.test {
    useJUnitPlatform()
    // The NeoForge unit-test runner changes the working directory, so structure tests need the real project path.
    systemProperty("incognita.projectDir", projectDir.absolutePath)
}

tasks.spotbugsTest {
    enabled = false // analyse shipped code only
}

tasks.jar {
    manifest.attributes(
        "Implementation-Title" to modName,
        "Implementation-Version" to modVersion,
    )
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
