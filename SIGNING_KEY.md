# MicUp Signing Key Documentation

> This document explains how MicUp's APK signing is set up, what the key details are, and how to manage it.

---

## What is APK signing?

Every Android app must be **signed** with a cryptographic key before it can be installed. The signing key is your app's identity — Android uses it to:

1. **Verify authenticity** — the APK came from you, not a hacker
2. **Allow updates** — a new APK can only replace an old one if both are signed with the same key
3. **Prevent tampering** — if anyone modifies the APK, the signature breaks

If you lose the signing key, you can NEVER update the app again. Users would have to uninstall the old version and install a fresh one. So **keep this document safe.**

---

## MicUp's Signing Key Details

| Property | Value |
|---|---|
| **Keystore file** | `micup-release.jks` (not in the repo — stored as GitHub Secret) |
| **Keystore type** | JKS (Java KeyStore) |
| **Key alias** | `coralx` |
| **Store password** | `TheWeeknd199` |
| **Key password** | `TheWeeknd199` |
| **Algorithm** | RSA 2048-bit |
| **Validity** | 9,125 days (25 years) — expires October 2051 |
| **Created** | October 31, 2026 |
| **Certificate subject** | `CN=MicUp, OU=Rajat, O=Rajatxo, L=India, ST=India, C=IN` |

---

## Where the key is stored

The keystore file itself is **NOT in the GitHub repo** (that would be a security risk). Instead, it's stored as an **encrypted GitHub Secret** in the repository settings.

### GitHub Secrets (configured)

These 4 secrets are set in https://github.com/rajatxo/MicUp/settings/secrets/actions:

| Secret name | What it contains |
|---|---|
| `CORAL_X_KEYSTORE_BASE64` | The entire `.jks` file, base64-encoded |
| `CORAL_X_STORE_PASSWORD` | `TheWeeknd199` |
| `CORAL_X_KEY_ALIAS` | `coralx` |
| `CORAL_X_KEY_PASSWORD` | `TheWeeknd199` |

> **Note:** The secret names say "CORAL_X" because Rajat uses the same keystore across all his apps per his handover doc. The actual key was generated fresh specifically for MicUp.

---

## How the build uses it

When GitHub Actions builds the APK, the workflow (`.github/workflows/build.yml`) does this:

1. **Decodes the keystore** from the `CORAL_X_KEYSTORE_BASE64` secret → writes `micup-release.jks` to disk
2. **Creates `keystore.properties`** with the passwords/alias from the other 3 secrets
3. **Gradle reads `keystore.properties`** (in `app/build.gradle.kts`) → signs the release APK with the key
4. **The signed APK** is published to GitHub Releases

If the secrets are missing (e.g., someone forks the repo), the build **falls back to debug signing** so the APK still installs — it just can't be updated over a release-signed version.

---

## How to verify the signing key

If you ever need to verify that an APK was signed with this key:

```bash
# On a Mac/Linux PC with keytool (comes with Java):
keytool -printcert -jarfile MicUp-v2.0.0-arm64.apk

# You should see:
# Owner: CN=MicUp, OU=Rajat, O=Rajatxo, L=India, ST=India, C=IN
# Issuer: CN=MicUp, OU=Rajat, O=Rajatxo, L=India, ST=India, C=IN
# SHA256: 97:6E:F1:9F:58:7D:EC:FD:F8:BF:52:2D:83:05:37:1D:96:0A:0B:61:B5:26:58:C1:69:89:4F:DE:FB:E5:2D:59
```

The SHA256 fingerprint `97:6E:F1:9F:...` is this key's unique identifier.

---

## How to back up the keystore

**If you lose the keystore, you can NEVER update MicUp again.** Back it up in multiple places:

### Option A: Re-download from GitHub Secrets
1. Go to https://github.com/rajatxo/MicUp/settings/secrets/actions
2. You can't download it directly (GitHub doesn't allow that), but you can update it if needed

### Option B: Regenerate it locally
If you have the keystore file (e.g., from when it was generated), store it in:
1. A password manager (Bitwarden, 1Password, LastPass) as a secure attachment
2. A USB drive in a drawer
3. Your Google Drive (in a password-protected zip)

### To regenerate the keystore locally:
```bash
keytool -genkeypair -v \
  -keystore micup-release.jks \
  -storetype JKS \
  -keyalg RSA \
  -keysize 2048 \
  -validity 9125 \
  -alias coralx \
  -storepass "TheWeeknd199" \
  -keypass "TheWeeknd199" \
  -dname "CN=MicUp, OU=Rajat, O=Rajatxo, L=India, ST=India, C=IN"
```

Then update the GitHub Secret:
```bash
# Base64-encode the keystore
base64 -w 0 micup-release.jks > keystore.b64

# Copy the contents, then go to:
# https://github.com/rajatxo/MicUp/settings/secrets/actions
# Update CORAL_X_KEYSTORE_BASE64 with the new value
```

---

## How to change the signing key (if compromised)

If the key ever gets leaked or compromised:

1. Generate a new keystore (see above)
2. Update all 4 GitHub Secrets with the new values
3. Push a new build — it will be signed with the new key
4. **Users will need to uninstall the old MicUp and install the new one** (because the signing key changed)
5. After that, all future updates work normally again

---

## Security notes

- ✅ The keystore file is **never** committed to the repo
- ✅ The `.gitignore` excludes `*.jks` and `keystore.properties`
- ✅ GitHub Secrets are encrypted at rest
- ✅ The passwords are not visible in build logs (GitHub masks secret values)
- ⚠️ **If you make the repo public**, the secrets stay private (only visible to repo admins)
- ⚠️ **Never paste the keystore file or passwords in chat, email, or any unencrypted channel**

---

## Summary

| What | Where |
|---|---|
| Keystore file (`micup-release.jks`) | GitHub Secret `CORAL_X_KEYSTORE_BASE64` (base64-encoded) |
| Store password | GitHub Secret `CORAL_X_STORE_PASSWORD` = `TheWeeknd199` |
| Key alias | GitHub Secret `CORAL_X_KEY_ALIAS` = `coralx` |
| Key password | GitHub Secret `CORAL_X_KEY_PASSWORD` = `TheWeeknd199` |
| Build config | `.github/workflows/build.yml` (decodes secrets, signs APK) |
| Gradle config | `app/build.gradle.kts` (reads `keystore.properties`, applies signing) |
| ProGuard rules | `app/proguard-rules.pro` (keeps Agora + serialization classes) |

**Keep this document safe.** If you lose the keystore, you lose the ability to update MicUp forever.
