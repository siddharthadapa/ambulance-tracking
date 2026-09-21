# Deploying this project

This gets you a real, publicly-reachable link for your resume: backend on
Railway (it gives you a managed MySQL database in the same project, which
matters since this app is written for MySQL specifically), frontend on
Vercel. Both have free tiers sufficient for a portfolio demo.

None of this can be automated from here - it requires your own GitHub,
Railway, and Vercel accounts. Everything below is what to click.

## 1. Push this project to GitHub

```bash
cd ambulance-tracking   # the folder containing both backend/ and frontend/
git init
git add .
git commit -m "Initial commit"
```
Create a new empty repo on GitHub, then:
```bash
git remote add origin https://github.com/<your-username>/ambulance-tracking.git
git branch -M main
git push -u origin main
```
This also activates the GitHub Actions CI workflow already included at
`.github/workflows/ci.yml` - check the "Actions" tab on GitHub after pushing
to confirm both the backend tests and frontend build pass.

## 2. Backend + database on Railway

1. Go to [railway.app](https://railway.app), sign in with GitHub.
2. **New Project → Deploy from GitHub repo** → select your repo.
3. Railway will try to auto-detect a build - since this is a monorepo with
   both `backend/` and `frontend/`, click into the new service's
   **Settings → Root Directory** and set it to `backend`. It will then find
   the `Dockerfile` there and build from it automatically.
4. In the same project, click **+ New → Database → Add MySQL**. Railway
   provisions a MySQL instance and gives it connection variables.
5. Go back to your backend service → **Variables** tab, and add:
   - `SPRING_DATASOURCE_URL` → `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&serverTimezone=UTC` (Railway lets you reference the MySQL service's variables directly like this)
   - `SPRING_DATASOURCE_USERNAME` → `${{MySQL.MYSQLUSER}}`
   - `SPRING_DATASOURCE_PASSWORD` → `${{MySQL.MYSQLPASSWORD}}`
   - `APP_JWT_SECRET` → generate a real random string (don't reuse the demo one from `application.properties`) - e.g. run `openssl rand -base64 48` locally and paste the output
   - `APP_CORS_ALLOWED_ORIGINS` → leave as `http://localhost:5173` for now; you'll come back and update this in step 4 once you have your real Vercel URL
6. Deploy. Once it's live, Railway gives you a public URL like
   `https://ambulance-tracking-backend-production.up.railway.app` - copy it,
   you'll need it in step 3.

## 3. Frontend on Vercel

1. Go to [vercel.com](https://vercel.com), sign in with GitHub.
2. **Add New → Project** → select the same repo.
3. Vercel will ask for the root directory - set it to `frontend`.
4. Framework preset: it should auto-detect Vite. Build command
   `npm run build`, output directory `dist` (these are Vite defaults and
   should already be correct).
5. Before deploying, add environment variables (**Environment Variables**
   section on the same import screen):
   - `VITE_API_BASE_URL` → `https://<your-railway-url>/api`
   - `VITE_WS_BASE_URL` → `https://<your-railway-url>/ws`
6. Deploy. Vercel gives you a URL like
   `https://ambulance-tracking-frontend.vercel.app`.

## 4. Close the loop: update backend CORS with the real frontend URL

Go back to Railway → your backend service → **Variables**, and update:
```
APP_CORS_ALLOWED_ORIGINS=https://ambulance-tracking-frontend.vercel.app
```
Redeploy the backend (Railway does this automatically when you save a
variable change). Without this step you'll hit the exact CORS error you saw
earlier in local dev, just against your production frontend URL instead of
localhost.

## 5. Verify

Open your Vercel URL, register a driver + patient account, and run through
the same flow you tested locally (driver goes online → patient requests →
live tracking). If registration/login fails, it's almost certainly one of:
- CORS mismatch (step 4 not done, or a typo in the URL - no trailing slash)
- Backend not actually up (check Railway's deploy logs)
- Database not connected (check `SPRING_DATASOURCE_*` variables reference
  the MySQL service correctly)

## Notes for your resume/README

- Put both live links directly in your GitHub README and on your resume -
  a clickable demo is worth far more than a screenshot.
- The free tiers on both platforms may sleep/spin down after inactivity -
  the first request after idle can take a few seconds. Worth mentioning to
  an interviewer if they click it cold.
