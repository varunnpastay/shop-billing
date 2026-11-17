# Shop Billing (Shri Vijaylaxmi Textiles, Ranebennur)

A billing app the owner uses on one shop PC, offline. Spring Boot 3.4.5, Java 17, Thymeleaf, H2 file database
(`./data/billingdb`), Tailwind CSS built from `ui/`, a WebGL peacock on the login page.

Read `docs/developer-guide.md` before changing code. `docs/user-guide.md` and `docs/admin-guide.md` describe the app
for the shop and for whoever installs it. Keep all three docs and `README.md` up to date with every change.

## Commands

- Build: `mvn -DskipTests package` → `target/shop-billing-1.0.0.jar`
- Test: `mvn test` (all must pass before merging)
- Run: `start.bat` or `java -jar target/shop-billing-1.0.0.jar` (port 8080; empty database logs in as admin / admin123)
- CSS: edit `ui/app.css` / `ui/fx.css`, then `cd ui && npm run build`. Never edit `static/css/app.css` by hand.

## Rules

- The user is a beginner; the app is for their father, who is not technical. Keep the UI simple, with big buttons
  and plain words. Keep existing behaviour working unless asked to change it.
- Never commit `data/`, `backups/`, `data-export/`, `db.properties`, `target/` or `ui/node_modules/`.
  The code repository is public; shop data goes only to the separate PRIVATE data repository via the daily export.
- Commit or push only when asked. Work on a branch, then fast-forward `main` and push.
- `.bat` files must stay CRLF.
- Theme colours: navy (`#1f4682` and the `navy` scale in `ui/tailwind.config.js`) and gold (`gold` scale).
  Dashboard pie colours are `PieChart.COLORS`.
- Login peacock performance settings (antialias off, `maxDpr = 1`, `maxBlurSamples = 1`, warm-up frames,
  `pk-flying` pausing the stars) were tuned for the slow shop PC; don't raise them.
