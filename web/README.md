# ShopKeep Live Web Companion

Real-time synchronized checklist viewer and editor powered by Firebase Firestore (`ai-studio-android-homekeep-8502e84b-2339-4853-b108-eaf44e3bbe94`).

## Public Link Setup (No Share Code & No Sign-In Needed)

1. Push the project to GitHub (`Push to GitHub` in AI Studio) so GitHub Pages updates with the latest `web/`, `docs/`, and root `index.html`.
2. Open the **ShopKeep** Android app, go to **Checklist -> My Stock Lists**, and tap **"Publish & Share Public Live Link (No Code)"** once.
3. In the Share dialog, tap the **Edit (pencil)** icon next to **PUBLIC LINK**, paste your GitHub Pages URL (for example `https://nandwanifamily242l-hash.github.io/<repo-name>`), and tap **Save**.
4. Share that plain URL with anyone — when they open it in any browser, it automatically loads your live shop checklist with **zero share codes** and **no Google sign-in alert**!

## Adding Restrictions Later

Whenever you want to restrict browser edits to signed-in users only:
- In the ShopKeep Android app, open **Checklist -> Shared Live Boards -> Public Link** and toggle **"Open Public Editing"** OFF.
- Visitors can still view the checklist in real time, and editing requires Google Sign-In (remember to add your GitHub Pages domain `nandwanifamily242l-hash.github.io` under **Firebase Console -> Authentication -> Settings -> Authorized domains** when enabling restrictions).
