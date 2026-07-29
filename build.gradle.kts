plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    // The patched java2typescript build is not published anywhere: it only exists in the local
    // Maven repository, put there by `mvn -pl core,processor -am install`. Scoped to that group so
    // nothing else in the build can silently resolve against a stale local artifact.
    mavenLocal {
        content { includeGroup("org.bsc.processor") }
    }
}

val zapVersion: String by project
val java2tsVersion: String by project
val addOnsDir: String by project
val zapExtensionsDir: String by project
val declaredPackages: String by project

// ---------------------------------------------------------------------------------------------
// Inputs
// ---------------------------------------------------------------------------------------------
//
// Two different things are needed, and conflating them is the usual mistake:
//
//   scan roots  -- the artifacts to ENUMERATE types from: the ZAP core jar and the add-on .zap
//                  files. Their dependencies are deliberately NOT scanned; a TypeScript
//                  declaration for commons-lang is not wanted.
//
//   classpath   -- everything that must be LOADABLE while generating. The processor resolves every
//                  scanned type and each of its supertypes through Class.forName() on its own
//                  annotation-processor classpath, so this is the scan roots PLUS all transitive
//                  dependencies. A type whose supertype is missing here is silently skipped.

// The ZAP core jar on its own: scanned, but its 28 transitive dependencies are not.
val zapCore: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

// ZAP core WITH its transitive dependencies, plus the java2ts processor.
val apiTypesClasspath: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

// A .zap add-on is a zip of class files, so it can be both scanned and put on the classpath as if
// it were a jar. Most add-ons shade their dependencies straight in; a few instead bundle them as
// nested jars under libs/, which no class loader can read in place -- see extractAddOnLibs.
val addOnFiles = fileTree(addOnsDir) { include("*.zap") }

val addOnLibsDir = layout.buildDirectory.dir("addon-libs")

dependencies {
    zapCore("org.zaproxy:zap:$zapVersion")

    apiTypesClasspath("org.zaproxy:zap:$zapVersion")
    apiTypesClasspath("org.bsc.processor:java2ts-processor:$java2tsVersion")
}

// A handful of add-ons (selenium, ...) ship their dependencies as jars inside the .zap rather than
// shaded into it. Those classes are unreachable while the jar sits nested in a zip, so an add-on
// type extending one of them cannot be introspected. Extract them onto the classpath -- NOT onto
// the scan roots, since third-party libraries are not part of the API being declared.
val extractAddOnLibs by tasks.registering {
    description = "Extracts jars bundled inside .zap files so their classes are loadable."

    val addOns = addOnFiles
    val outDir = addOnLibsDir

    inputs.files(addOns).withPropertyName("addOns")
    outputs.dir(outDir)

    doLast {
        val target = outDir.get().asFile
        target.deleteRecursively()
        target.mkdirs()

        var extracted = 0
        addOns.files.forEach { addOn ->
            zipTree(addOn).matching { include("libs/*.jar") }.forEach { jar ->
                // Prefix with the add-on name: two add-ons may bundle the same library at
                // different versions, and the last copy written would otherwise win silently.
                jar.copyTo(target.resolve("${addOn.nameWithoutExtension}-${jar.name}"), overwrite = true)
                extracted++
            }
        }
        logger.lifecycle("Extracted {} bundled jar(s) from {} add-on(s).", extracted, addOns.files.size)
    }
}

// ---------------------------------------------------------------------------------------------
// Generation
// ---------------------------------------------------------------------------------------------

val apiTypesOutputDir = layout.buildDirectory.dir("generated/zap-api-types")

tasks.register<JavaCompile>("generateTypes") {
    group = "build"
    description = "Generates TypeScript declarations for ZAP core and the add-ons in $addOnsDir."

    // The trigger source. Nothing is compiled to .class (-proc:only): it exists only to carry the
    // @Java2TS annotation, without which javac never invokes the processor. The types themselves
    // are discovered by -Ats.scan below.
    source = fileTree("src/apiTypes/java")

    dependsOn(extractAddOnLibs)

    val scanRoots = files(zapCore, addOnFiles)
    val resolveClasspath =
        files(scanRoots, apiTypesClasspath, fileTree(addOnLibsDir) { include("*.jar") })

    classpath = resolveClasspath
    options.annotationProcessorPath = resolveClasspath

    // The scan roots decide the output but are only named inside compilerArgs, which Gradle cannot
    // see into. Declare them so adding or replacing a .zap file re-runs the task.
    inputs.files(scanRoots).withPropertyName("scanRoots")
    inputs.property("declaredPackages", declaredPackages)

    options.generatedSourceOutputDirectory.set(apiTypesOutputDir)
    // Unused with -proc:only, but JavaCompile still requires a destination.
    destinationDirectory.set(layout.buildDirectory.dir("classes/api-types"))

    doFirst {
        if (addOnFiles.isEmpty) {
            logger.warn(
                "No .zap files in {}/ -- generating core types only. Run `gradlew syncAddOns` to " +
                    "copy them from {}.",
                addOnsDir,
                zapExtensionsDir,
            )
        }
        logger.lifecycle("Scanning {} artifact(s).", scanRoots.files.size)
    }

    options.compilerArgs =
        listOf(
            "-proc:only",
            "-processor",
            "org.bsc.processor.TypescriptProcessor",
            "-Ats.outfile=zap-api",
            "-Ats.registry=zapApi",
            "-Ats.scan=" + scanRoots.joinToString(File.pathSeparator),
            "-Ats.scan.include=$declaredPackages",
            // Annotation processing emits notes of its own and the declared JDK types include
            // deprecated members; none of it should fail or clutter the build.
            "-Xlint:none",
            "-nowarn",
        )
}

// ---------------------------------------------------------------------------------------------
// Convenience
// ---------------------------------------------------------------------------------------------

tasks.register<Copy>("syncAddOns") {
    group = "build"
    description = "Copies the built .zap files from $zapExtensionsDir into $addOnsDir."

    val source = file(zapExtensionsDir).resolve("addOns")

    from(source) {
        include("*/build/zapAddOn/bin/*.zap")
    }
    // Flatten: the add-on id and version are already in the file name.
    eachFile { path = name }
    includeEmptyDirs = false
    into(addOnsDir)

    doFirst {
        require(source.isDirectory) {
            "no add-ons at $source -- set zapExtensionsDir in gradle.properties, and build the " +
                "add-ons first (./gradlew assembleZapAddOn in the zap-extensions checkout)"
        }
    }
}

tasks.register<Delete>("cleanAddOns") {
    group = "build"
    description = "Removes the copied .zap files from $addOnsDir."
    delete(fileTree(addOnsDir) { include("*.zap") })
}
