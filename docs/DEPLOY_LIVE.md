# Live deploy checklist (next time)

Chrome Web Store item: [Developer dashboard](https://chrome.google.com/webstore/devconsole/ec84b97a-0b60-45a9-98be-a0e99caabbe0)

The store package is a **ZIP**, not a JAR. The backend JAR is built inside Docker on the GCP VM and served at `https://www.catalogstudio.in`.

## A. Chrome extension (ZIP → Web Store)

1. From the repo root:

```powershell
powershell -File scripts/build-extension-prod.ps1
```

2. Confirm the zip exists: `catalog-studio-extension/release/catalog-studio-extension-<version>.zip`  
   (version must be **higher** than the last upload).
3. Optional local check: `chrome://extensions` → Developer mode → Load unpacked → `catalog-studio-extension/dist`. Pair at `https://www.catalogstudio.in/extension`.
4. Open the [item dashboard](https://chrome.google.com/webstore/devconsole/ec84b97a-0b60-45a9-98be-a0e99caabbe0) and sign in as `reathkumar66602@gmail.com`.
5. **Package** (or **Upload new package**) → choose that zip. `manifest.json` is at the zip root.
6. Fill remaining listing fields if Google shows errors:
   - Privacy policy: `https://www.catalogstudio.in/privacy`
   - Official site: `https://www.catalogstudio.in`
   - Support: `https://www.catalogstudio.in/contact`
   - Icon: `catalog-studio-extension/public/icons/icon128.png`
   - Screenshot: 1280×800 or 640×400 of the Meesho fill flow
7. **Distribution**: Unlisted (first publish) or Public. Region: India.
8. **Submit for review**. Wait for the email. After approval, copy the public `chromewebstore.google.com/detail/...` URL onto `/chrome-extension`.

First publish can take 1–3 days. Later version bumps are usually faster.

## B. Backend JAR + dashboard (GCP VM)

Do this on the VM (`/opt/catalog-studio`), not on your Windows PC. Docker builds `catalog-studio-backend-1.0.0-SNAPSHOT.jar` from `catalog-studio-backend/Dockerfile` and runs it as `java -jar`.

1. Copy the new code onto the VM (git pull or file sync) into `/opt/catalog-studio`.
2. Rebuild and restart:

```bash
cd /opt/catalog-studio
docker compose --env-file .env.gcp -f docker-compose.yml -f docker-compose.gcp.yml up -d --build
```

3. Check:

```bash
docker compose -f docker-compose.yml -f docker-compose.gcp.yml ps
curl -sS https://www.catalogstudio.in/api/v1/site
```

4. If only the API changed, `--build backend` is enough. If the dashboard/privacy page changed, also rebuild `dashboard`.
5. Do **not** copy a Windows `target/*.jar` onto the VM unless you also match Java 21 and `SPRING_PROFILES_ACTIVE=prod` plus `.env.gcp`. Prefer Docker.

Optional local JAR (dev only, not the live site):

```powershell
$env:JAVA_HOME = "D:\jdk\jdk-21.0.10"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
cd catalog-studio-backend
mvn -DskipTests package
java -jar target/catalog-studio-backend-1.0.0-SNAPSHOT.jar
```

## Order when both change

1. Deploy JAR/dashboard on the VM and confirm `https://www.catalogstudio.in` works.
2. Build the prod extension zip (it already points at that URL).
3. Upload the zip on the same Web Store item and submit.
