// Written with Claude AI
// Nothing to run explicitly, generateAppIcons is liked to the pre-build phase
// How to run it manually "./gradlew app:generateAppIcons"

tasks.register("generateAppIcons") {
    description = "Resizes app_icon_1024_1024.png into all mipmap density buckets as WebP."
    group = "build setup"
    val srcIcon = file("../mwm_private/app_icon_1024_1024.png")
    val resDir = file("src/main/res")
    inputs.file(srcIcon).optional()
    outputs.dir(resDir)
    doLast {
        if (!srcIcon.exists()) {
            logger.lifecycle("[icons] src not found (${srcIcon.path}) — skipping")
            return@doLast
        }
        checkCwebpAvailable()
        val iconNames = listOf("ic_launcher", "ic_launcher_foreground")
        val densityToSize = mapOf(
            "mipmap-mdpi" to 48,
            "mipmap-hdpi" to 72,
            "mipmap-xhdpi" to 96,
            "mipmap-xxhdpi" to 144,
            "mipmap-xxxhdpi" to 192
        )
        densityToSize.forEach { (density, size) ->
            val dir = File(resDir, density).also { it.mkdirs() }
            iconNames.forEach { name ->
                File(dir, "$name.png").delete()
                runCwebp(srcIcon, File(dir, "$name.webp"), size)
            }
            logger.lifecycle("[icons] $density  ${size}×${size}px")
        }
        logger.lifecycle("[icons] Done")
    }
}

fun checkCwebpAvailable() {
    val available = try {
        ProcessBuilder("cwebp", "-version").redirectErrorStream(true).start().waitFor() == 0
    } catch (_: Exception) {
        false
    }
    check(available) { "cwebp not found — install libwebp (brew install webp)" }
}

fun runCwebp(input: File, output: File, size: Int) {
    val proc = ProcessBuilder(
        "cwebp", "-lossless",
        "-q", "100",
        "-resize", "$size", "$size",
        input.absolutePath,
        "-o", output.absolutePath
    ).inheritIO().start()
    check(proc.waitFor() == 0) { "cwebp failed for ${output.name}" }
}

tasks.named("preBuild") { dependsOn("generateAppIcons") }
