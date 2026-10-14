// Takes a screenshot of @Preview composables using Roborazzi (no device needed).
// Falls back to a fake screenshot using the app icon if rendering fails.

// 👉 How to run it: ./gradlew takeScreenshotPreview
// 👉 Output: ./mwm_private/screenshot.png

tasks.register("takeScreenshotPreview") {
    group = "mwm"
    description = "Renders @Preview composables via Roborazzi (no device needed), falling back to the app icon."
    dependsOn("recordRoborazziDebug")
    doLast {
        val outputFile = rootProject.file("mwm_private/screenshot.png")
        val png = project.file("build/outputs/roborazzi/preview.png")
        if (png.exists()) {
            png.copyTo(outputFile, overwrite = true)
            logger.lifecycle("[screenshot] Saved → ${outputFile.path}")
        } else {
            logger.lifecycle("[screenshot] No Roborazzi screenshot found — falling back to icon")
            createIconScreenshotPreview(outputFile)
        }
    }
}

fun createIconScreenshotPreview(outputFile: File) {
    val srcIcon = rootProject.file("mwm_private/app_icon_1024_1024.png")
    if (!srcIcon.exists()) {
        logger.lifecycle("[screenshot] app_icon_1024_1024.png not found — skipping")
        return
    }
    val proc = try {
        ProcessBuilder(
            "convert",
            "-size", "1080x1920", "xc:#121212",
            srcIcon.absolutePath,
            "-resize", "540x540",
            "-gravity", "Center",
            "-composite",
            outputFile.absolutePath
        ).redirectErrorStream(true).start()
    } catch (_: Exception) {
        srcIcon.copyTo(outputFile, overwrite = true)
        logger.lifecycle("[screenshot] Copied raw app icon as screenshot fallback")
        return
    }
    val exitCode = proc.waitFor()
    if (exitCode == 0) {
        logger.lifecycle("[screenshot] Created icon-based screenshot with ImageMagick")
    } else {
        srcIcon.copyTo(outputFile, overwrite = true)
        logger.lifecycle("[screenshot] ImageMagick failed — copied raw icon as fallback")
    }
}
