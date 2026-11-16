# Shop Billing – Shri Vijaylaxmi Textiles

Billing software for a textile shop, with **two billing systems in one app**. It runs on the shop computer and opens in
the web browser.

- **Local bill** (`+ Local Bill`): the small counter receipt for retail customers. It shows Item, Qty, MRP, Rate and
  Amount, the total savings, the amount in words and a UPI payment QR code. Bills are numbered `0001/2026`,
  `0002/2026`… and the numbering starts again at 1 every year.
- **Wholesale bill** (`+ Wholesale Bill`): the full A4/A5 GST tax invoice.

**Full documentation:** [User guide](docs/user-guide.md) · [Admin guide](docs/admin-guide.md) ·
[Developer guide](docs/developer-guide.md)

## Features

- **Login:** the animated peacock from the shop logo flies in, moves about until you log in, then flies away and the
  dashboard opens.
- **Dashboard:** an animated banner, today's total (local and wholesale), money to collect and to pay, pie charts for
  this month, low-stock alerts, best sellers and the 5 latest bills with a Print button.
- **Local bills:** live receipt preview, Save & Print, saved bills, daily sales report. Item names fill in the MRP and
  rate; customer names fill in the mobile number. You can search, reprint, cancel and restore bills.
- **GST bills (wholesale):** CGST + SGST, IGST or no GST, with discount, round-off and the amount in words (lakh/crore).
  Cash or credit bills, Original and Duplicate copies, A4 or A5 paper, and Save as PDF.
- **Items and stock:** code, category, saree type, fabric, colour, design, photo and barcode; purchase, selling,
  wholesale and MRP prices; stock in pieces and bundles with a minimum-stock alert.
  - Stock goes down with every bill, comes back when a bill is cancelled or goods are returned, and goes up with
    purchases.
  - Corrections made with "Adjust stock" need a reason.
- **Purchases and suppliers:** supplier invoices, payments to suppliers, and what is still owed to each supplier.
- **Customers:** retail or wholesale type, bill history and payments received (set against the oldest credit bills
  first), with **Payment Reminders** sent through WhatsApp.
- **Returns, expenses, accounts and profit:**
  - Money in and out by Cash / UPI / Bank.
  - Daily income and expenses.
  - An estimated profit and loss.
- **Reports:** sales by day, month or year, best sellers, purchases, stock value, profit and outstanding amounts.
  Each one exports to Excel (CSV) and can be printed.
- **Activity history:** price changes, stock adjustments, cancellations, deletions and payments.
- **Backups:** once a day on the computer, plus twice a day to a private GitHub repository (see Backup).
- **Security:** passwords stored with BCrypt, a new password required at first login, protection against cross-site
  requests, and 8-hour sessions.

## Folder layout

```
shop-billing/
├─ start.bat            start the app (double-click)
├─ restore.bat          restore a backup (drag a backup .zip onto it)
├─ autostart-on.bat     start the app by itself when the computer starts (autostart-off.bat undoes it)
├─ db.properties.example  optional settings (port, backup time)
├─ pom.xml              Java build (Maven)
├─ src/main/java/com/smme/billing/
│   ├─ config/          login check, security, error pages, web settings
│   ├─ controller/      one controller per screen (bills, local bills, items, customers, reports, settings…)
│   ├─ model/           the data (bill, item, customer, supplier, purchase, payment, expense, return…)
│   ├─ repository/      database access
│   └─ service/         the business logic (billing, stock, profit, backups, Excel export…)
├─ src/main/resources/
│   ├─ templates/       the pages (Thymeleaf HTML)
│   ├─ static/css       app.css (built from ui/) and peacock-login.css
│   ├─ static/js        page scripts; peacock-engine.js + vendor/gsap for the login animation
│   ├─ static/img       logos, dashboard artwork; img/peacock/ holds the login peacock layers
│   └─ application.properties
├─ src/test/            automatic tests (they use a separate in-memory database)
├─ docs/                user, admin and developer guides
└─ ui/                  Tailwind CSS source; builds static/css/app.css (only needed to change the look)

Created while running (never committed):
  data/          the shop's database (billingdb.mv.db)
  backups/       automatic backups (newest 30 kept)
  data-export/   the daily Excel + database copy that is pushed to GitHub
```

## Requirements

- **Java 17 or newer** (to run the app)
- **git**, signed in to GitHub (only for the daily GitHub backup)
- **Maven 3.8+** (only to build from source)

## Start

1. **Build** (only when building from source): `mvn -DskipTests package`
2. **Start:** double-click `start.bat`. It opens http://localhost:8080 in the browser after a few seconds. Keep the
   black window open while the shop is working.
3. **Start automatically (optional):** double-click `autostart-on.bat` once. The app will then start by itself when
   the computer starts.

The data is stored in the `data` folder next to the app. Nothing else needs to be installed.

Other computers in the shop on the same network can open `http://<this-computer's-IP>:8080`. Allow port 8080 in
Windows Firewall if needed. To use a different port, set `PORT=8081` in `db.properties` (see `db.properties.example`).

## First login

| Username | Password |
|---|---|
| `admin` | `admin123` |

After the first login you must set a new password. Then open **Settings** and enter the shop name, address, GSTIN,
bank details, starting bill numbers and logo.

## Printing

- **Local bill:** click **Save & Print Bill**. The bill is saved, then the print window shows only the receipt. Click
  **New Bill** for the next customer.
- **Wholesale bill:** open the bill and click **Print Bill**. You can also click **Print Small (A5)** or
  **Print Duplicate Copy**. To make a PDF, choose **Save as PDF** as the printer. Pick the paper size that matches the
  bill.

## Backup

**On this computer:** a backup is made once a day (the app checks every 6 hours) into the `backups` folder, and the
newest 30 are kept.
**Settings → Backup** has a *Backup now* button and download links.

**Off this computer, to GitHub (recommended):** at 1 PM and 8 PM, and a few minutes after the app starts if that day's
backup is missing, the app saves two things and pushes them to a **private** GitHub repository:

- all the data as an **Excel** file, to read: `YYYY/MM/shop-data-YYYY-MM-DD.xlsx` and `latest.xlsx`
- the full **database**, to restore: `database/shop-billing-database.zip`

To set it up once:

1. On GitHub, create a new repository (e.g. `shop-billing-data`) and choose **Private**. It holds customer details.
2. Install git on the shop computer and sign in to GitHub.
3. In the app go to **Settings → Daily backup to GitHub**, paste the link and click **Save link**, then click
   **Export & push now**.

To change the times, set `EXPORT_CRON` in `db.properties`.

**To restore a backup:**

1. Close the app (the black window).
2. Drag a backup `.zip` onto **restore.bat**. Use one from the `backups` folder, or `database/shop-billing-database.zip`
   downloaded from GitHub.
3. Start the app again.

The current data is kept in a `data_before_restore_<date-time>` folder, so nothing is lost. On a new computer, copy
the app folder, install Java, then restore the database from GitHub the same way.

## Changing the look (Tailwind CSS)

The finished stylesheet `src/main/resources/static/css/app.css` is already built, so the shop computer needs nothing
extra. To change the look, edit `ui/app.css`, `ui/fx.css` or the templates, then run:

```
cd ui
npm install        (first time only, needs Node.js)
npm run build      (rebuilds src/main/resources/static/css/app.css)
```

Then rebuild the app with `mvn package`.

## Tests

```
mvn test
```

The tests use a separate in-memory database. They load sample data and go through the whole app:

- login and settings
- items, customers, suppliers and purchases
- bills, stock, payments, returns and expenses
- printing, reports and exports
- the Excel backup
- error pages

## Troubleshooting

| Problem | Fix |
|---|---|
| `Port 8080 was already in use` | The app is already running (look for the black window), or another program uses the port. Close it, or set `PORT=8081` in `db.properties`. |
| "Export failed: git push failed" | Make sure git is installed and signed in to GitHub, and that the repository link is right and **Private**. |
| Forgot the password | Close the app, then run the command below in the app folder. Log in with `admin123` and set a new password. |

```
java -cp target\shop-billing-1.0.0.jar -Dloader.main=org.h2.tools.Shell org.springframework.boot.loader.launch.PropertiesLauncher -url jdbc:h2:file:./data/billingdb -user sa -sql "UPDATE SHOP_SETTING SET PASSWORD_HASH='240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9' WHERE ID=1"
```

The long value in that command is the stored form of the password `admin123`. The app upgrades it to BCrypt
automatically when you log in.
