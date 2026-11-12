# Shop Billing

Billing software for small shops and traders with **two billing systems in one app**:

- **Local bill** (`+ Local Bill`): the small counter receipt for retail customers — Item, Qty, MRP, Rate, Amount, total savings, amount in words and a UPI payment QR code. Numbered `0001/2026`, `0002/2026`… and starts again at 1 every year.
- **Wholesale bill** (`+ Wholesale Bill`): the full A4/A5 GST tax invoice.

It runs in a web browser, and the data is stored in the database.

## Features

- **Local bills:** same entry screen and receipt as the old Smart Billing page (live preview, Print Bill, Saved Bills → Load, Daily Sales Report), but bills are now saved in the database instead of the browser. Item names fill MRP and rate from the Items list; customer names fill the mobile number. Search, reprint, cancel and restore under **Local Bills**.
- **GST bills (wholesale):** CGST + SGST, IGST or no GST. Supports a discount, round-off and amount in words (Indian numbering: lakh/crore).
- **Cash and credit bills:** track unpaid (credit) bills and mark them paid later.
- **Printing:** full-page A4 or A5 bill with your shop logo, Original and Duplicate copies, and *Save as PDF*.
- **Customers and items:** saved lists that fill in automatically while you bill. New customers are saved automatically.
- **Bill tools:** search by bill number, customer, date or status. Edit, copy to a new bill, cancel and restore bills.
- **Items and stock:** item code, category, saree type, fabric, colour, design, photo and barcode; purchase, selling, wholesale and MRP prices; stock in pieces and bundles with a minimum-stock alert. Stock goes down with every local and wholesale bill, comes back when a bill is cancelled or goods are returned, and goes up with purchases. Manual corrections ("Adjust stock") need a reason. Search by name, code, category, fabric, colour or barcode.
- **Wholesale pricing:** picking an item on a wholesale bill fills its wholesale price; local bills use the selling price.
- **Payment method:** Cash, UPI or Bank transfer on every bill, payment and expense.
- **Purchases and suppliers:** record supplier invoices (stock goes up), payments made to suppliers, and the balance still owed to each supplier.
- **Customer accounts:** retail or wholesale type, full bill history, payments received (set against the oldest credit bills first), outstanding balance, and a **Payment Reminders** list with one-click WhatsApp reminders.
- **Returns:** goods brought back by a customer go back into stock; the refund is taken off sales.
- **Expenses and accounts:** rent, electricity, transport and other expenses; **Accounts & Profit** shows money received and paid out by Cash / UPI / Bank, a daily income-and-expense table, an estimated profit and loss (sales − cost of goods − expenses), money to collect and money to pay.
- **Activity history:** price changes, stock adjustments, bill cancellations, deletions and payments are recorded with date, time and user.
- **Dashboard:** today, this month and this year sales, stock value, low-stock alerts, purchases and expenses, estimated profit, money to collect and to pay, and best-selling items.
- **Reports:** bills (local and wholesale GST), daily / monthly / yearly sales, best sellers (by item, category and fabric), purchases, stock valuation, profit and loss, and customer / supplier outstanding. Every report exports to Excel (CSV) and prints or saves as PDF.
- **Shop settings:** name, address, GSTIN, bank details, terms, starting bill numbers (wholesale and local), logo upload, and the local receipt text (title, line under shop name, declaration, footer, Instagram ID, UPI ID for the QR code).
- **Security:** passwords stored with BCrypt, the default password must be changed on first login, protection against cross-site requests, and 8-hour sessions.

## Requirements

| Software | Version |
|---|---|
| Java (JDK or JRE) | 17 or newer |
| PostgreSQL (optional) | 12 or newer |
| Maven (only to build from source) | 3.8+ |

## Installation

1. **Build** (only when building from source):
   ```
   mvn -DskipTests package
   ```

2. **Start.** Double-click `start.bat`. It opens http://localhost:8080 in the browser after a few seconds. Keep the black window open while the shop is working.

That's all. By default the app uses its **built-in database**: your data is stored in the `data` folder next to the app and backed up automatically (see Backup). Nothing else needs installing.

**Optional – PostgreSQL instead of the built-in database:** create the database once with
`psql -U postgres -c "CREATE DATABASE shop_billing;"`, copy `db.properties.example` to `db.properties` and set
`DB_PASSWORD`. You can also set the environment variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `PORT`.
Existing data in the built-in database is not copied over automatically.

**Start automatically with Windows:** press `Win + R`, type `shell:startup`, and put a shortcut to `start.bat` in that folder.

## First login

| Username | Password |
|---|---|
| `admin` | `admin123` |

After the first login you must set a new password before you can use the app. Then open **Settings** and enter your shop name, address, GSTIN, bank details and starting bill number, and upload your logo.

## Printing and PDF

**Local bill:** click **Print Bill**. The bill is saved first, then the print window opens with only the receipt. Use **New Bill** for the next customer. Printing an opened bill again updates that same bill (it does not create a new number).

**Wholesale bill:**

On a bill, click **Print A4** or **Print A5**, then **Print / Save as PDF**.
- To get a PDF, choose **Save as PDF** as the printer.
- Choose the paper size that matches the bill (A4 or A5).
- The bill is designed to fill exactly one page.

## Using other computers in the shop

Other computers on the same network can open `http://<this-computer's-IP>:8080`. Allow port 8080 in Windows Firewall if needed.

## Backup

**Built-in database (default):** a backup is made automatically every day the app runs, into the `backups` folder (the newest 30 are kept). **Settings → Backup** has a *Backup now* button and download links. Copy a backup to a pen drive or Google Drive every week – a backup on the same computer does not help if the computer's disk fails.

To restore: close the app (the black window), drag the backup `.zip` from the `backups` folder onto **restore.bat**, then start the app again. The current data is kept in a `data_before_restore_<date-time>` folder, so nothing is lost.

**PostgreSQL:** back up daily with

```
pg_dump -U postgres -F c -f shop_billing_backup.dump shop_billing
```

and restore with `pg_restore -U postgres -d shop_billing --clean shop_billing_backup.dump`.

## Changing the look (Tailwind CSS)

The screens are styled with [Tailwind CSS](https://tailwindcss.com). The finished stylesheet
`src/main/resources/static/css/app.css` is already built and committed, so the shop computer needs
nothing extra. Only when you change the look:

```
cd ui
npm install        (first time only, needs Node.js)
npm run build      (rebuilds src/main/resources/static/css/app.css)
```

Edit `ui/app.css` (and the templates); then run `npm run build` and `mvn package`.

## Running the tests

```
mvn test
```

The tests use an in-memory database and walk through the whole app: login, password change, settings, logo, items, customers, bills, printing, reports, CSV, error pages and logout.

## Troubleshooting

| Problem | Fix |
|---|---|
| `SCRAM-based authentication, but no password was provided` | `DB_PASSWORD` is missing. Check `db.properties` and make sure you start the app from the same folder. |
| `database "shop_billing" does not exist` | Do step 1 of Installation. |
| `Port 8080 was already in use` | Another program (or another copy of this app) is using the port. Close it, or set `PORT=8081` in `db.properties`. |
| Forgot password | Run this in `psql`, then log in with `admin123` and set a new password:<br>`UPDATE shop_setting SET password_hash = '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9' WHERE id = 1;`<br>The long value is the hash of `admin123`; it is upgraded to BCrypt automatically on login. |
