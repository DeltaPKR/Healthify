# Healthify — Privacy-policy hosting via GitHub Pages

The privacy policy lives at `docs/privacy/index.html` in this repo. Once you
enable GitHub Pages from the repo's `main` branch with the `/docs` folder as
source, the policy is live at:

```
https://deltapkr.github.io/Healthify/privacy/
```

This is the URL already wired into `AndroidManifest.xml` (Health Connect
metadata) and the Play Console / store-listing docs.

---

## Click-by-click setup (one-time, ~2 minutes)

1. **Merge this branch to `main`** so the `docs/` folder lives on the
   default branch. Push to GitHub.

2. Open the repo on github.com:
   👉 https://github.com/DeltaPKR/Healthify

3. Click **Settings** (top right tab — gear icon).

4. In the left sidebar, click **Pages**.

5. **Source** section:
   - **Branch:** `main`
   - **Folder:** `/docs`
   - Click **Save**.

6. Wait ~30 seconds. The page reloads with a green banner:
   > _Your site is live at `https://deltapkr.github.io/Healthify/`_

7. Verify the live URLs:
   - Root (redirects): https://deltapkr.github.io/Healthify/
   - Policy: https://deltapkr.github.io/Healthify/privacy/

   Both should load with no auth.

That's it. The policy is now publicly hosted on Microsoft / GitHub's
infrastructure at zero cost.

---

## Updating the policy later

Edit `docs/privacy/index.html` (or `docs/PRIVACY_POLICY.md` and re-render
the HTML by hand). Commit + push to `main`. GitHub Pages re-publishes
within ~1 minute.

Bump the `<strong>Last updated:</strong>` date inline in the HTML when
making material changes.

---

## Optional: custom domain (if you buy `healthify.app` later)

1. Register `healthify.app` at any registrar (Namecheap / Cloudflare /
   Porkbun — about $10–$15/year for `.app` TLDs).

2. At the registrar's DNS panel, create:
   - **CNAME** record: `www` → `deltapkr.github.io`
   - **A** records at apex (`@`) pointing to GitHub Pages' IPs:
     ```
     185.199.108.153
     185.199.109.153
     185.199.110.153
     185.199.111.153
     ```

3. Back in repo **Settings → Pages**, enter `healthify.app` in the
   **Custom domain** field and click **Save**. Tick **Enforce HTTPS**
   once the TLS certificate has provisioned (~5–10 minutes).

4. Update `AndroidManifest.xml:57` (the
   `health_connect_request_permissions_privacy_policy_url` meta-data
   entry) to the new URL. Update `docs/PLAY_CONSOLE.md` and
   `docs/STORE_LISTING.md` as well. Rebuild the release AAB:
   ```bash
   ./gradlew.bat bundleRelease
   ```

---

## Why not Cloudflare Pages / Netlify / Vercel?

All three work fine for a single static HTML file. GitHub Pages wins
here because:

- You're already pushing to GitHub.
- No new account or CLI to install.
- Free, no rate limits relevant for a privacy policy.
- The URL is stable forever as long as the repo exists.

If you ever prefer one of the others, just point its build to the
`docs/` folder of this repo and update the manifest + Play Console URL
accordingly.
