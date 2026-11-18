# ShopKeep Live Web Companion

Real-time synchronized checklist viewer and editor powered by Firebase Firestore (`ai-studio-android-homekeep-8502e84b-2339-4853-b108-eaf44e3bbe94`).

## No-Command-Line Deployment (No Cloud Shell Needed)

### Option 1: Netlify Drop (Fastest — ~10 seconds)
1. Download the project ZIP and extract it.
2. Open **https://app.netlify.com/drop** in any browser.
3. Drag and drop the **`web`** folder onto the page.
4. Copy the live URL Netlify gives you (e.g., `https://your-site.netlify.app`).
5. In the ShopKeep Android app, open **Checklist -> Shared Live Boards -> Share URL**, tap the pencil icon next to **LIVE WEB URL**, and paste your Netlify URL once.

### Option 2: Push to GitHub + GitHub Pages / Vercel / Cloudflare Pages
1. Use **Push to GitHub** in AI Studio.
2. Either enable **GitHub Pages** in repository **Settings -> Pages** or connect the repo in **Vercel** / **Cloudflare Pages** (set publish directory to `web`).

> **Note for Live Editing in Browser:** Viewing any board (`?checklist=SHARE_CODE`) works immediately on any host. To also allow Google Sign-In editing on a custom domain (like `your-site.netlify.app`), add that domain under **Firebase Console -> Authentication -> Settings -> Authorized domains**.
