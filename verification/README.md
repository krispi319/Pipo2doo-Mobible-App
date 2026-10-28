# Verification evidence

The logs and backend status describe checks run during preparation, not a signed APK build. The JavaScript/Kotlin harness files preserve the test inputs and assertions; their `.verify140`/`.verify139` paths refer to the preparation workspace.

The normal project tests are supplied under app/src/test/kotlin. In your existing configured Android project, run `./gradlew :app:testDebugUnitTest` (Windows: `gradlew.bat :app:testDebugUnitTest`) or use Android Studio's test runner. This also includes the Robolectric repository tests, which were not run in the preparation environment.

Do not post logs containing your passwords, private Firebase JSON, signing key or user tokens when reporting a device issue. The included logs contain none of those.
