# StreamHub: GitHub CI/CD Remote Configuration & Cloudflare Guide

This guide explains how to connect StreamHub to GitHub so that future site additions and config updates are automatically validated, cryptographically signed, and published to your app over the air.

---

## 1. Connecting Your Local Repository to GitHub

### Step 1: Create a GitHub Repository
1. Go to [github.com/new](https://github.com/new).
2. Create a new repository (e.g., `StreamHub` or `TheDesiTadka`).
3. Leave it empty (do NOT initialize with README, license, or .gitignore).

### Step 2: Push Local Code to GitHub
Open PowerShell or your terminal in this project folder:
```powershell
# Add your GitHub remote URL (replace <your-username> and <your-repo>)
git remote add origin https://github.com/<your-username>/<your-repo>.git

# Ensure branch is main
git branch -M main

# Commit all project files
git add .
git commit -m "feat: integrate all providers, Cloudflare challenge solver, and GitHub CI/CD"

# Push to GitHub
git push -u origin main
```

---

## 2. Setting Up GitHub Actions Signing Secret

Your Android app cryptographically validates all remote configurations using Ed25519 signatures. To enable automated signing on GitHub:

1. Copy the content of your local private key:
   - Open `config-tools/keys/ed25519_private.pem` (this file is excluded from Git via `.gitignore` to keep your key secure).
2. On GitHub:
   - Navigate to **Settings** > **Secrets and variables** > **Actions**.
   - Click **New repository secret**.
   - **Name**: `ED25519_SIGNING_KEY`
   - **Secret**: Paste the full PEM content (including `-----BEGIN PRIVATE KEY-----` and `-----END PRIVATE KEY-----`).
   - Click **Add secret**.
3. (Optional) Enable GitHub Pages:
   - Navigate to **Settings** > **Pages**.
   - Under **Build and deployment** > **Source**, select **GitHub Actions**.

---

## 3. How to Add a New Site or Update Config via GitHub

Whenever you want to add a new streaming site or update selectors:

1. Open `config-tools/sample-manifest.json` on GitHub (or locally).
2. Increment the `"configVersion"` number (e.g. from `103` to `104`).
3. Add or modify your provider configuration in the `"providers"` array.
4. Commit your changes to the `main` branch.

### What GitHub Actions Does Automatically:
The workflow `.github/workflows/publish-config.yml` triggers instantly:
- **Validates** schema, capabilities, and SSRF/security rules (`validate_config.py`).
- **Canonicalizes** and **signs** the configuration with Ed25519 (`sign_config.py`).
- **Updates** `config-tools/signed-manifest.json` in the repository and publishes to GitHub Pages.

---

## 4. Syncing the Updated Config in the Android App

In the StreamHub Android app:
1. Open **Settings** (gear icon in the top right).
2. Under **REMOTE CONFIGURATION**:
   - Check the **Config Source URL**.
   - Default: `https://raw.githubusercontent.com/<username>/<repo>/main/config-tools/signed-manifest.json`
   - (Or your GitHub Pages URL: `https://<username>.github.io/<repo>/signed-manifest.json`).
3. Tap **Sync Now**.
4. The app verifies the signature, updates the provider engine, and the new site appears immediately on your Home feed without rebuilding the APK!

---

## 5. Cloudflare / CAPTCHA Protection & Solver

Sites protected by Cloudflare Turnstile or Managed Challenge (such as `fry99.cc` or `aagmaal.com`) require human-in-the-loop verification:

1. **Automatic Detection**: When a site returns a Cloudflare challenge (`HTTP 403` with `cf-mitigated: challenge`), the app displays a prominent **Security Verification Required** card.
2. **One-Tap Verification**: Tap **Verify Site Access**. An in-app browser window opens with the site's verification page.
3. **Solve the Checkbox**: Tap the Cloudflare "Verify you are human" checkbox.
4. **Auto-Clearance**: Once Cloudflare grants clearance, the window automatically captures the `cf_clearance` and `__cf_bm` cookies, flushes them to the app's network client, and returns to the feed.
5. **Proactive Solving**: You can also proactively verify any site in **Settings** > **PROVIDER MANAGEMENT** by tapping **Solve Cloudflare / CAPTCHA** next to any provider.
