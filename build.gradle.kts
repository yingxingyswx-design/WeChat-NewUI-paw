plugins {
    id("com.android.application") version "9.4.1" apply false
}

// Keep Gradle output out of the source tree mirrored by publish.ps1.
layout.buildDirectory.set(layout.projectDirectory.dir(".gradle-build/root"))
