# Pipo2doo — automatic release APK

This replaces only `.github/workflows/build-apk.yml`. No app source, layouts, backend data or version settings change.

The original workflow was fetched from your public repository on 30 October 2026. It had a manual-only assembleDebug build and uploaded a test workflow artifact. This replacement preserves that manual option and adds release APK automation.

## 1. Install the workflow

Extract the ZIP and merge its `.github` folder into your actual Pipo2doo repository folder. Replace `.github/workflows/build-apk.yml`. Commit and push that workflow along with your updated app source to your repository's default branch.

## 2. Configure the original signing key once

You need the SAME `.jks` or `.keystore` file, alias and passwords used to sign the installed app. Firebase service-account keys and google-services.json are not app-signing keys.

In GitHub repository Settings → Secrets and variables → Actions → New repository secret, create these exact four secrets:

| Secret | Value |
|---|---|
| ANDROID_KEYSTORE_BASE64 | Base64 representation of the original signing keystore file |
| ANDROID_KEYSTORE_PASSWORD | Password for that keystore |
| ANDROID_KEY_ALIAS | Alias of the key inside the keystore |
| ANDROID_KEY_PASSWORD | Password for that key; often the same as the keystore password |

Do not commit the keystore or passwords to your repository. You can use PowerShell + the authenticated GitHub CLI instead of pasting the keystore's encoded text into the browser. Replace the local file path:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\YOUR_FOLDER\original-key.jks")) | gh secret set ANDROID_KEYSTORE_BASE64 --repo krispi319/Pipo2doo-Mobible-App
gh secret set ANDROID_KEYSTORE_PASSWORD --repo krispi319/Pipo2doo-Mobible-App
gh secret set ANDROID_KEY_ALIAS --repo krispi319/Pipo2doo-Mobible-App
gh secret set ANDROID_KEY_PASSWORD --repo krispi319/Pipo2doo-Mobible-App
```

The last three commands prompt for their values. This does not require a personal access token in the workflow: its scoped GITHUB_TOKEN uploads the APK.

If the installed APK came from the old GitHub Actions test build, STOP before choosing a signing key. That old workflow did not persist/configure a stable debug keystore. A different runner's default debug key may differ from the installed APK's certificate. Locate the actual original key if you have it; a newly generated key cannot update an APK signed by another key. Do not uninstall your original app as a workaround without confirming its backed-up data first.

## 3. Publish a new release — no local APK needed

First commit/push the app's version settings, source and the workflow. For v1.4.1, app/build.gradle.kts must have versionName 1.4.1 and versionCode 151. Keep the existing application ID.

For a tag/release that does NOT already exist, run:

```powershell
gh release create v1.4.1 --repo krispi319/Pipo2doo-Mobible-App --title "Pipo2doo v1.4.1" --generate-notes --latest
```

Publishing the release triggers the workflow. It checks out the exact tag, builds assembleRelease, checks the APK versionName matches the tag, aligns/signs/verifies the APK, and uploads:

- Pipo2doo-v1.4.1.apk
- Pipo2doo-v1.4.1.apk.sha256

The release is visible before the build finishes; its APK appears after a successful build. The in-app updater cannot download that new version until the APK asset exists. The next update check can retry.

For every future version, bump both versionName and versionCode in the app source BEFORE publishing its matching tag. Renaming a release does not change the compiled app version.

## If v1.4.1 already exists without an APK

Do not delete/recreate it. After committing the new workflow to the default branch and configuring secrets, trigger the existing-tag build:

```powershell
gh workflow run build-apk.yml --repo krispi319/Pipo2doo-Mobible-App -f release_tag=v1.4.1
```

It builds the source already referenced by that tag. If that old tag points to the wrong version, create a correctly versioned new release from the correct committed source rather than moving a released tag. For an existing asset, this workflow intentionally refuses to overwrite its APK silently. Immutable releases may also reject later uploads; use a new correctly prepared release if necessary.

## Watch/download

Open the repository's Actions tab → Build APK → Build signed release APK. A green run means the signed APK was built/uploaded (inspect Attach signed APK to existing release if there is an upload failure). The signed artifact is also retained in the workflow for 14 days.

Open the release:

```powershell
gh release view v1.4.1 --repo krispi319/Pipo2doo-Mobible-App --web
```

Your existing manual test build remains available: run Build APK with release_tag empty. It produces the original test APK artifact and does not publish it as a release update.

## Validation and requirements

YAML parsing, trigger/job structure checks and bash -n for every shell block passed locally. The actual cloud Android/signing/upload run has NOT been executed here. No file was pushed, no secret was set and no release was published by this delivery.

The workflow preserves your existing Java 17, Android SDK, Gradle, icon tooling and action versions. Existing required project/vendor inputs must still be in the repository. Release builds additionally check the Supabase client configuration file exists. Missing signing secrets fail clearly instead of publishing an unsigned/random-key APK.

GitHub-hosted runner build time depends on project/dependency availability. The app's layout/data are unaffected by this workflow change; installable update compatibility depends on the original signing key, unchanged application ID and increasing versionCode.
