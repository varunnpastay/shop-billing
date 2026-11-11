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
- **Dashboard:** today's and this month's local and wholesale sales, GST collected and pending credit.
- **Reports:** local sales and wholesale GST report for any period, each with export to Excel (CSV).
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

To restore: close the app, delete `data/billingdb.mv.db`, unzip the backup into the `data` folder, and start the app again.

**PostgreSQL:** back up daily with

```
pg_dump -U postgres -F c -f shop_billing_backup.dump shop_billing
```

and restore with `pg_restore -U postgres -d shop_billing --clean shop_billing_backup.dump`.

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
