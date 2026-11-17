# Developer guide

For anyone who changes the code. It covers how the app is built and where things are, plus the business rules the
code follows.

**Contents:**

1. [Technology](#1-technology)
2. [Building, running, testing](#2-building-running-testing)
3. [Project layout](#3-project-layout)
4. [Request flow and cross-cutting pieces](#4-request-flow-and-cross-cutting-pieces)
5. [Data model](#5-data-model)
6. [Business rules](#6-business-rules)
7. [Background jobs](#7-background-jobs)
8. [Login page and the peacock animation](#8-login-page-and-the-peacock-animation)
9. [Dashboard effects](#9-dashboard-effects)
10. [Styles (Tailwind)](#10-styles-tailwind)
11. [Configuration](#11-configuration)
12. [Web addresses (routes)](#12-web-addresses-routes)
13. [Tests](#13-tests)
14. [Conventions and gotchas](#14-conventions-and-gotchas)

---

## 1. Technology

| Part | Choice |
|---|---|
| Language / runtime | Java 17 (runs on newer JDKs) |
| Framework | Spring Boot 3.4.5: web MVC, Thymeleaf, Spring Data JPA (Hibernate) |
| Database | H2, file mode (`./data/billingdb`), `ddl-auto=update` |
| Passwords | `spring-security-crypto` (BCrypt). Spring Security itself is not used. |
| Excel export | Apache POI 5.3.0 (`poi-ooxml`) |
| Pages | Server-rendered Thymeleaf templates plus small plain-JavaScript files (no front-end framework) |
| CSS | Tailwind CSS 3.4.17, built ahead of time into `static/css/app.css` (source in `ui/`) |
| Login animation | WebGL (own small engine) plus GSAP 3.12.5 (bundled as `static/js/vendor/gsap-3.12.5.min.js`) |
| Fonts | Cinzel (shop name) and Gelasio (login card), bundled as woff2 |
| Receipt QR | `qrcode.min.js` (bundled, license file next to it) |

Everything the browser needs is served by the app, so it works offline.

## 2. Building, running, testing

```
mvn -DskipTests package        # builds target/shop-billing-1.0.0.jar (a runnable fat jar)
java -jar target/shop-billing-1.0.0.jar          # or start.bat
mvn test                       # 9 tests, in-memory database
```

While working on templates, CSS or JavaScript, run the jar against the live files so changes show on reload:

```
java -jar target/shop-billing-1.0.0.jar --server.port=8083 ^
  --spring.web.resources.static-locations=file:src/main/resources/static/ ^
  --spring.thymeleaf.prefix=file:src/main/resources/templates/ ^
  --spring.thymeleaf.cache=false --spring.web.resources.chain.cache=false
```

## 3. Project layout

```
src/main/java/com/smme/billing/
  BillingApplication.java     entry point (@SpringBootApplication, @EnableScheduling)
  config/
    WebConfig.java            registers LoginInterceptor; paths that need no login (/login, /css, /js, /img, /fonts, /shop-logo, /error)
    LoginInterceptor.java     redirects to /login without a session; while on the default password only /settings and /logout are allowed
    SecurityFilter.java       blocks non-GET requests whose Origin/Referer host differs from Host (or X-Forwarded-Host); sets nosniff, SAMEORIGIN, same-origin referrer
    GlobalModel.java          adds shop (settings), hasLogo, currentPath and lowStockCount to every page
    GlobalExceptionHandler.java  friendly messages for oversized uploads
  controller/                 one per area: Auth, Dashboard, Bill, LocalBill, Product, Customer, Supplier,
                              Purchase, Expense, Return, Accounts (also /activity), Report, Settings,
                              Backup, Export, Logo
  model/                      JPA entities (see Data model)
  repository/                 Spring Data JPA interfaces
  service/
    BillService               wholesale bills: validate, calculate GST, save (+stock), paid/cancel, receive payments, outstanding
    LocalBillService          local bills: numbering NNNN/YYYY, calculate, save (+stock), cancel
    StockService              stock in/out by item name
    PurchaseService           purchases (+stock), supplier payments and balances
    InsightService            dashboard/report figures: best sellers, stock summary, profit & loss, money in/out, period tables, receivables/payables
    SettingService            the single settings row, login check, password change, defaults
    ActivityService           writes the activity log (with the logged-in user)
    BackupService             daily H2 backup zip, Backup now, backupTo(file)
    DataExportService         Excel workbook + database zip into data-export/, git commit and push
    Money                     ₹ formatting with Indian grouping (bean "money" in templates)
    NumberToWords             amount in words (lakh/crore)
    PieChart                  server-side SVG donut data for the dashboard
src/main/resources/
  application.properties
  templates/                  Thymeleaf pages; fragments.html = <head>, menu, flash messages, status badges, printer icon
  static/css/app.css          built Tailwind output (do not edit by hand)
  static/css/peacock-login.css  login page styles (plain CSS)
  static/js/                  bill.js (wholesale form), local-bill.js (local form + receipt), fx.js (dashboard effects),
                              peacock-engine.js + peacock-login.js (login), qrcode.min.js, vendor/gsap
  static/img/                 logos, dashboard-art.jpg, brand-lakshmi.jpg (invoice), img/peacock/ (login layers + rig.json)
  static/fonts/               Cinzel, Gelasio
src/test/java/com/smme/billing/
  EndToEndTest, ShopFeaturesTest, SampleDataTest, service/BillCalculationTest, service/NumberToWordsTest
  config/SampleDataLoader     test-only sample data (active with property sample-data=true)
ui/                           Tailwind source: app.css (components), fx.css (animations), tailwind.config.js
```

## 4. Request flow and cross-cutting pieces

1. **SecurityFilter** runs on every request. It adds security headers and rejects cross-site POSTs.
2. **LoginInterceptor** checks the session attribute `user`. It redirects to `/login`, or to `/settings#password`
   while `mustChangePassword` is set.
3. The controller loads data through the services. **GlobalModel** adds the shop settings and menu badge data.
4. A Thymeleaf template renders the page. Static file URLs get a content hash
   (`spring.web.resources.chain.strategy.content`), so browsers never show an old CSS or JS file.

**Login** (`AuthController`):

- `POST /login` checks the username and the BCrypt hash. A legacy SHA-256 hash is accepted once and upgraded.
- On success it creates a new session (to prevent session fixation) and redirects to `/`. If the password is the
  default one, it redirects to `/settings#password` instead.
- A wrong login returns the login page with `error`. The login page script uses `fetch` with `redirect: "manual"`, so
  a redirect means success.

## 5. Data model

All money fields are `BigDecimal`. Times use `LocalDate` and `LocalDateTime`.

| Entity | Main fields | Notes |
|---|---|---|
| `ShopSetting` (id 1) | shop details, nextBillNo, defaultGst, receipt texts, upiId, nextLocalBillNo, localBillYear, username, passwordHash, exportGitUrl | Single row, created with defaults on first start |
| `ShopLogo` (id 1) | data (≤1 MB), contentType | Uploaded logo |
| `Product` | name (unique), code, category, sareeType, fabric, color, design, barcode, hsn, unit, purchasePrice, rate, wholesalePrice, mrp, stockQty, minStock, piecesPerBundle, hasImage | `stock()`, `stockValue()`, `isLowStock()`, `getBundleText()` |
| `ProductImage` (id = product id) | data, contentType | Item photo |
| `Customer` | name, customerType (RETAIL/WHOLESALE), phone, address, gstin, stateName | Bills refer to customers **by name** |
| `Supplier` | name, phone, address, gstin, notes | |
| `Bill` + `BillItem` | billNo (unique), billDate, customer fields, billType (CASH/CREDIT), paymentMode, taxType (CGST_SGST/IGST/NONE), gstPercent, subTotal, discount, taxableAmount, cgst, sgst, igst, roundOff, grandTotal, amountReceived, paid, paidDate, cancelled, byName, notes; items: particulars, details, hsn, qty, unit, rate, amount | `getBalance()` = grandTotal − amountReceived (0 if paid or cancelled) |
| `LocalBill` + `LocalBillItem` | billNo (`NNNN/YYYY`, unique), billYear, seqNo, billDate, customerName, custMobile, paymentMode, totalQty, totalSale, totalSavings, netPayable, cancelled; items: name, qty, mrp, rate, amount | Always paid |
| `Purchase` + `PurchaseItem` | supplierId, supplierName, invoiceNo, purchaseDate, otherCharges, total; items: productName, qty, rate, amount | |
| `Payment` | kind (CUSTOMER/SUPPLIER), partyId, partyName, payDate, amount, mode, note, billId | Customer receipts and supplier payments |
| `Expense` | expenseDate, category, amount, mode, note | |
| `SalesReturn` | returnDate, billRef, customerName, productName, qty, refund, mode, reason | |
| `ActivityLog` | at, userName, action, details | Append-only history |

Schema changes are applied by Hibernate (`ddl-auto=update`): new columns and tables are added, and nothing is
dropped. Adding a field to an entity is therefore safe for existing shops. Renaming or removing one is not.

## 6. Business rules

### Wholesale bill (`BillService`)

- **Validation:**
  - The customer name is required.
  - There must be at least one line with a name.
  - Each quantity must be > 0 and each rate ≥ 0.
  - GST must be from 0 to 100.
  - The discount must be from 0 to the sub total.
- **Calculation:**
  - Line amount = qty × rate, rounded to 2 decimals.
  - Taxable amount = sub total − discount.
  - `CGST_SGST`: CGST = SGST = taxable × GST% ÷ 200 each. `IGST`: taxable × GST% ÷ 100. `NONE`: no tax.
  - Grand total = taxable + taxes, rounded to the nearest rupee (HALF_UP). The difference is stored as round-off.
- **Numbering:** `ShopSetting.nextBillNo`. A number that already exists is skipped, and the setting is moved past
  it.
- **Cash or credit:**
  - Cash bills are marked paid on the bill date.
  - A new credit bill, or a cash bill changed to credit, is unpaid.
  - Editing a credit bill keeps its paid state.
- **Mark paid** on a credit bill records a `Payment` for the balance, with `billId` set. **Mark unpaid** deletes the
  payments that have that `billId`.
- **Receive payment** (by customer name):
  - Refused if it is more than the outstanding amount.
  - Otherwise it is set against unpaid credit bills, oldest first (`amountReceived`). A bill whose balance reaches 0
    becomes paid.
- **Outstanding** = the sum of the balances of the customer's unpaid, non-cancelled credit bills.

### Local bill (`LocalBillService`)

- Amount = qty × rate. Total sale = sum of amounts. Savings = (qty × MRP total) − sale, minimum 0. Net payable = sale.
- Numbering is `NNNN/YYYY` per bill year, using `nextLocalBillNo` / `localBillYear` from settings.
- Saving a bill with a name and a mobile number adds the customer if they're new.
- The receipt QR code is `upi://pay?pa=<upiId>&cu=INR&am=<net payable>&tn=<shop> Bill <no>`.

### Stock (`StockService`)

- Bill, purchase and return lines are matched to items **by name, ignoring case**. Unmatched lines change nothing.
- Saving a bill takes its quantities out. Editing first puts the old quantities back, then takes the new ones out.
- Cancel puts the quantities back and restore takes them out again. A purchase adds stock; deleting it removes the
  stock again. A return adds stock.
- Stock may go negative. Items can't be edited to change stock; only *Adjust stock* (which requires a reason and is
  logged) changes it.

### Money and profit (`InsightService.summary`)

- **Sales** = the wholesale taxable amount + local net payable, of non-cancelled bills. **Net sales** = sales − refunds.
- **Cost of goods** = Σ quantity sold × the item's *current* purchase price, minus returned quantities at the same
  price. Items without a purchase price count as 0 cost and are counted in `itemsWithoutCost`.
- **Gross profit** = net sales − cost of goods. **Net profit** = gross profit − expenses.
- **Money in**, by mode: cash-type wholesale bills, local bills, and customer receipts.
- **Money out**, by mode: expenses, supplier payments, and refunds.
- **Receivables** = the total of the outstanding amounts. **Payables** = Σ positive supplier balances (purchases −
  payments).

### Other

- **Payment reminder** links are `https://wa.me/<91 + 10-digit mobile>?text=…`. The message includes the amount, the
  date and the UPI ID. There is no link without a usable mobile number.
- A supplier with purchases or payments can't be deleted. Deleting a customer keeps their bills.
- The logo and item photos must be PNG, JPEG, WEBP or GIF (checked by file signature) and ≤ 1 MB.

## 7. Background jobs

| Job | When | What |
|---|---|---|
| `BackupService.dailyBackup` | 1 min after start, then every 6 h | If there is no backup for today, `BACKUP TO 'backups/shop_billing_<stamp>.zip'`. Keeps the newest 30. |
| `DataExportService.daily` | `export.cron` (default 1 PM and 8 PM) | Excel + database zip into `data-export/`, then git add/commit/push |
| `DataExportService.onStartup` | 5 min after start | Runs the export if today's Excel file is missing |

The export:

- clones the repository on first use (or turns an existing `data-export/` into a clone);
- runs `git pull --rebase` before writing;
- commits only when something changed;
- pushes with `HEAD:main`.

Git runs through `ProcessBuilder` with `GIT_TERMINAL_PROMPT=0` and a 3-minute timeout. Only `https://github.com/<owner>/<repo>` links are accepted.

## 8. Login page and the peacock animation

`templates/login.html` is a standalone page. It doesn't use the app menu or `app.css`; its styles are in
`css/peacock-login.css`.

- **Background:** navy gradients, two line-art mandalas (path generated in `peacock-login.js`), 70 seeded stars and 4
  sparkles.
- **Logo:** `img/peacock/logo-plate.webp` is the logo with the peacock and saree removed. A full-screen WebGL canvas
  (`#peacockHost`) draws them back on top, exactly in place.
- **Engine** (`js/peacock-engine.js`, exposes `window.SVTPeacock`), made of:
  - WebGL helpers;
  - a 2D bone rig with linear-blend skinning: 24 bones from `rig.json`, plus extra wing copies;
  - Catmull-Rom flight paths;
  - the stage renderer (skinned body, wing layers, cloth-grid saree, gold particles, optional motion blur);
  - the GSAP choreography (fly-in, idle actions on two channels, error reaction, success display and fly-away).
- **Page script** (`js/peacock-login.js`):
  1. Builds the background, loads `rig.json`, creates the stage, and draws 4 invisible warm-up frames.
  2. Starts the fly-in. The stars pause for 3.3 s while the bird flies.
  3. Handles the login form. On success it plays the fly-away, fades the screen, sets
     `sessionStorage['svt-from-login']` and opens `/`; the dashboard then plays a short settle-in.
- **Performance defaults:**
  - no anti-aliasing;
  - device-pixel-ratio cap 1;
  - motion blur off (`maxBlurSamples = 1`);
  - `powerPreference: "high-performance"`.

  `stage.setQuality({blurSamples, maxDpr})` can raise them. On a 60 Hz screen this runs at about 57–60 fps even
  with software rendering.
- **Fallbacks:**
  - no WebGL, or the context is lost: the still `logo-original.webp` is shown and login still works;
  - `prefers-reduced-motion`: no flight, just fades.

## 9. Dashboard effects

`templates/dashboard.html` with `js/fx.js` and the rules in `ui/fx.css`:

- **Banner:** a crop of `dashboard-art.jpg` (x 130–1360, y 10–530 of 1536×1024), placed in the `.welcome-art` box.
  The flames, lamp glows and rotating halo are positioned in percentages of that crop: `x = (px − 130) / 1230`,
  `y = (py − 10) / 520`.
- **Moving extras:** rose petals and sparkles are created by `fx.js`. The money figures count up (`data-countup`).
  The pie charts draw themselves; they are SVG built from `PieChart` on the server, with no chart library.
  Their colours are `PieChart.COLORS`, in the app's theme: navy `#1f4682`, gold `#e0a12a`, teal `#2a9d8f`,
  maroon `#b0476b`, sky blue `#6f9fd8`, grey `#9aa5b8` (the sixth slice, usually "Other").
- All effects stop under `prefers-reduced-motion`.

## 10. Styles (Tailwind)

- **Sources** are in `ui/`:
  - `app.css`: base styles and components (`@layer components`), with `@import "./fx.css"` for the animations.
  - `tailwind.config.js`: the navy and gold colours and the fonts. Content scanning covers the templates and the
    JavaScript files.
- **Build:** `cd ui && npm install && npm run build` writes the minified `src/main/resources/static/css/app.css`. Commit
  the built file; the shop computer never needs Node.
- **Purging:** classes only added from JavaScript must appear literally in a scanned file, or live outside
  `@layer` (as `fx.css` does).

## 11. Configuration

`application.properties`, with optional overrides from `./db.properties` or environment variables:

| Property | Default | Notes |
|---|---|---|
| `server.port` | `${PORT:8080}` | |
| `spring.datasource.url` | `jdbc:h2:file:./data/billingdb;AUTO_SERVER=TRUE` | Relative to the working directory |
| `backup.dir` / `backup.keep` | `./backups` / `30` | |
| `export.dir` / `export.cron` | `./data-export` / `0 0 13,20 * * *` | `EXPORT_DIR` / `EXPORT_CRON` |
| `server.servlet.session.timeout` | `8h` | |
| `spring.servlet.multipart.max-file-size` | `2MB` | Logo and photos are also limited to 1 MB in code |
| `spring.web.resources.chain.strategy.content.enabled` | `true` | Content-hashed static URLs, cached for 30 days |

## 12. Web addresses (routes)

All need a login except `/login`, the static files and `/shop-logo`. Changes use POST.

| Area | Routes |
|---|---|
| Auth | `GET/POST /login`, `POST /logout` |
| Dashboard | `GET /` |
| Wholesale bills | `GET /bills` (search: `q`, `from`, `to`, `status`), `/bills/new`, `/bills/{id}`, `/bills/{id}/edit`, `/bills/{id}/copy`, `/bills/{id}/print?size=A4\|A5&copy=ORIGINAL\|DUPLICATE`; `POST /bills/save`, `/bills/{id}/paid`, `/bills/{id}/cancel` |
| Local bills | `GET /local`, `/local/new`, `/local/{id}?print=true`; `POST /local/save` (JSON), `/local/{id}/cancel`; JSON: `GET /local/api/bills/{id}`, `/local/api/saved`, `/local/api/next`, `/local/api/report?date=` |
| Items | `GET /products` (`q`, `low`), `/products/new`, `/products/{id}/edit`, `/products/{id}/image`; `POST /products/save`, `/products/{id}/stock`, `/products/{id}/delete` |
| Customers | `GET /customers`, `/customers/new`, `/customers/{id}`, `/customers/{id}/edit`, `/customers/outstanding`; `POST /customers/save`, `/customers/{id}/receive`, `/customers/{id}/delete` |
| Suppliers | `GET /suppliers`, `/suppliers/new`, `/suppliers/{id}`, `/suppliers/{id}/edit`; `POST /suppliers/save`, `/suppliers/{id}/pay`, `/suppliers/{id}/payments/{paymentId}/delete`, `/suppliers/{id}/delete` |
| Purchases | `GET /purchases`, `/purchases/new`, `/purchases/{id}`, `/purchases/{id}/edit`; `POST /purchases/save`, `/purchases/{id}/delete` |
| Expenses / Returns | `GET/POST /expenses`, `POST /expenses/{id}/delete`; `GET/POST /returns`, `POST /returns/{id}/delete` |
| Accounts / Activity | `GET /accounts?from=&to=`, `GET /activity` |
| Reports | `GET /reports?tab=sales\|summary\|items\|purchases\|stock\|profit\|outstanding&from=&to=&group=DAY\|MONTH\|YEAR`; CSV: `/reports/csv`, `/reports/local-csv`, `/reports/export?type=summary\|items\|purchases\|stock\|profit\|outstanding` |
| Settings | `GET/POST /settings`, `POST /settings/password`, `/settings/logo`, `/settings/logo/delete`; backups: `POST /settings/backup`, `GET /settings/backup/{name}`; GitHub export: `POST /settings/export/link`, `/settings/export/run`, `GET /settings/export/latest` |
| Logo | `GET /shop-logo` |

## 13. Tests

`mvn test` runs **9 tests** against in-memory H2 databases:

| Test | Covers |
|---|---|
| `EndToEndTest` | The whole flow: login and forced password change, settings, logo, items, customers, wholesale and local bills, printing, reports, CSV, static files, error pages, logout |
| `ShopFeaturesTest` | Stock in and out (bills, edits, cancel/restore, purchases, returns, adjustments), supplier balances, customer payments (part, over-payment refused, full), profit and money-in figures, activity log, every page, exports, cross-site and forwarded-host checks |
| `SampleDataTest` | Loads `SampleDataLoader` data, checks stock arithmetic and balances, the dashboard (split, pies, old tiles gone), supplier form errors, ~150 pages incl. prints and exports, and the Excel workbook (all sheets, row counts) |
| `BillCalculationTest` | GST maths: CGST/SGST, IGST, discount, round-off (4 tests) |
| `NumberToWordsTest` | Amount in words, lakh/crore (2 tests) |

The tests don't cover the WebGL animation or the git push. Check those by hand: open `/login`, and click Settings →
*Export & push now*.

## 14. Conventions and gotchas

- **Items are matched by name.** Bills store names, not item IDs. Renaming an item breaks the link for later stock
  moves of older bills.
- **Customers on bills are stored by name too.** Balances and history group by the customer name (ignoring case).
- **Don't edit `static/css/app.css` by hand.** Change `ui/` and rebuild.
- **Line endings:** `.bat` files must be CRLF (enforced in `.gitattributes`).
- **Never commit** `data/`, `backups/`, `data-export/` or `db.properties`; they are listed in `.gitignore`.
- **Activity log:** call `ActivityService.log(action, details)` for anything a shop owner would want to trace later.
- **The `money` bean** in templates: `${@money.inr(x)}` gives `1,20,790.50`, `${@money.rs(x)}` the rupees part,
  `${@money.qty(x)}` a quantity without trailing zeros.
