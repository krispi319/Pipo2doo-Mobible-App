These are standalone JVM platform test doubles, not production Android classes.
Compile AccountBackup.kt with Android.kt Util.kt Data.kt Tone.kt Api.kt Test.kt, plus Kotlin stdlib, kotlinx-coroutines-core-jvm and org.json. Run ai.mwm.client.cloud.TestKt.
CareRoundtrip.kt contains the actual care serializer/restore methods extracted from the production file for standalone round-trip verification; run it separately.
The test doubles do not validate Android Keystore, Compose rendering, real HTTP authentication, phone permissions or installation behavior.
