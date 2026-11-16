# Admin guide

For the person who installs and looks after the app: setup, settings, backups, updates and fixing problems.

**Contents:**

1. [What is installed where](#1-what-is-installed-where)
2. [First-time setup](#2-first-time-setup)
3. [Settings page](#3-settings-page)
4. [Starting, stopping, starting automatically](#4-starting-stopping-starting-automatically)
5. [Backups on the computer](#5-backups-on-the-computer)
6. [Daily backup to GitHub](#6-daily-backup-to-github)
7. [Restoring data](#7-restoring-data)
8. [Moving to a new computer](#8-moving-to-a-new-computer)
9. [Updating the app](#9-updating-the-app)
10. [Optional settings file (db.properties)](#10-optional-settings-file-dbproperties)
11. [Using it from other computers in the shop](#11-using-it-from-other-computers-in-the-shop)
12. [Forgotten password](#12-forgotten-password)
13. [Security notes](#13-security-notes)
14. [Troubleshooting](#14-troubleshooting)

---

## 1. What is installed where

**Requirements:**

- **Java 17 or newer.** <https://adoptium.net> works well; choose the JRE or JDK for Windows.
- **git, signed in to GitHub.** Needed only for the GitHub backup.
- **A modern browser:** Edge or Chrome. The login animation needs WebGL; without it, the login shows the still logo
  and still works.

**The app folder (`shop-billing`):**

| Path | What it is |
|---|---|
| `start.bat` | Starts the app and opens the browser after about 15 seconds |
| `restore.bat` | Restores a backup (drag a backup `.zip` onto it) |
| `autostart-on.bat` / `autostart-off.bat` | Turn starting with Windows on or off |
| `target\shop-billing-1.0.0.jar` | The app itself (built by Maven; `start.bat` also accepts it next to `start.bat`) |
| `db.properties.example` | Optional settings (port, backup time) |
| `data\billingdb.mv.db` | **All the shop's data** (bills, customers, items, payments, settings, password) |
| `backups\` | Automatic daily backups (newest 30 kept) |
| `data-export\` | The local copy of the GitHub backup repository |
| `docs\` | These guides |

`data`, `backups` and `data-export` are created by the app and are never uploaded to the code repository.

> The data folders are found relative to the folder the app is started from. `start.bat` always starts it from the app
> folder. Starting the jar from another folder would create a new, empty `data` folder there.

## 2. First-time setup

1. Copy the `shop-billing` folder to the shop computer and install Java.
2. Double-click `start.bat`. The browser opens `http://localhost:8080`.
3. Log in with **admin / admin123**. The app only allows the Settings page until a new password is set.
4. **Settings → Change Password:** the old password (`admin123`), then the new password twice. It must be at least 6
   characters and must not be `admin123`. Write it down and keep it safe.
5. **Settings → Shop Settings:** shop name, address, GSTIN, mobile, email, state and state code, the **next wholesale
   bill number**, the default GST % (5), bank details and terms.
6. **Settings → Wholesale Bill Header** and **Local Bill (small receipt):** the receipt title, the line under the shop
   name, the declaration, the footer, the Instagram ID, the **UPI ID** (for the QR code on receipts) and the **next
   local bill number**.
7. **Settings → Shop Logo:** upload the logo (PNG, JPG, WEBP or GIF, under 1 MB). If none is uploaded, the built-in SVT
   logo is used.
8. **Items:** add the main items with purchase, selling and wholesale prices and opening stock.
9. **Customers / Suppliers:** add the regular ones. Customers are also added automatically from bills.
10. **Printers:** print one local and one wholesale bill to set the paper size and margins in the print window. The
    browser remembers them.
11. Set up the [GitHub backup](#6-daily-backup-to-github) and [automatic start](#4-starting-stopping-starting-automatically).
12. Set Windows **Settings → System → Power & sleep → Sleep: Never** (when plugged in).

## 3. Settings page

| Section | Contents |
|---|---|
| **Shop Settings** | Name, address, GSTIN, contact, state, next wholesale bill number, default GST, bank details, terms |
| **Wholesale Bill Header** | Shop name in the local language, blessing line, invoice address, second mobile, jurisdiction |
| **Local Bill (small receipt)** | Title, subtitle, declaration, footer, Instagram, UPI ID, show logo, next local bill number |
| **Shop Logo** | Upload or remove the logo |
| **Change Password** | Old, new and confirm |
| **Backup** | *Backup now*, and the latest backups with download links |
| **Daily backup to GitHub** | Repository link, *Export & push now*, *Download latest Excel*, last result |

Bill numbers:

- **Wholesale bills** continue from *next bill number*. A number already used is skipped.
- **Local bills** are `NNNN/YYYY`. They start again at `0001` each year. Setting *next local bill number* changes the
  next one for the current year.

## 4. Starting, stopping, starting automatically

- **Start:** double-click `start.bat`. A black window opens; keep it open. The browser opens by itself.
- **Stop:** close the black window. Everything already saved stays saved.
- **Start with Windows:** double-click `autostart-on.bat` once. This puts a shortcut in the Windows *Startup*
  folder, so the app starts when someone signs in to Windows. `autostart-off.bat` removes it.
- **Already running?** If `start.bat` shows *Port 8080 was already in use*, the app is already open. Look for the
  black window, or just open `http://localhost:8080`.

## 5. Backups on the computer

- **Automatic:** once a day. The app checks a minute after it starts, and then every 6 hours. A zip named
  `shop_billing_YYYY-MM-DD_HHMMSS.zip` goes into `backups\`, and the newest **30** are kept.
- **By hand:** **Settings → Backup → Backup now**. The list there has a **Download** link for each backup.
- A backup is a complete copy of the database. It is restored with `restore.bat` (see [Restoring data](#7-restoring-data)).

> These backups are on the **same disk** as the data. If the disk fails, both are lost. That is why the
> GitHub backup exists. You can also copy a backup to a pen drive now and then.

## 6. Daily backup to GitHub

At **1 PM and 8 PM**, and once about 5 minutes after the app starts if that day's backup is missing, the app does
this:

1. It writes `data-export\YYYY\MM\shop-data-YYYY-MM-DD.xlsx` and `data-export\latest.xlsx`. This is **all the data
   as Excel**, to read. The sheets are:
   - Summary, Items, Customers, Suppliers
   - Wholesale Bills and Wholesale Bill Lines
   - Local Bills and Local Bill Lines
   - Purchases and Purchase Lines
   - Payments, Expenses, Returns
2. It writes `data-export\database\shop-billing-database.zip`. This is **the full database**, to restore. It is the
   same kind of file as the backups in `backups\`.
3. It runs `git add`, `commit` and `push` to your repository. GitHub keeps every earlier version.

**Setting it up once:**

1. On GitHub: **New repository**. Name it e.g. `shop-billing-data`, choose **Private**, and don't add any files.
2. On the shop computer: install git and make sure it can push to GitHub. A `git push` in any repository should work
   without asking for a password. Git Credential Manager remembers the login after the first time.
3. In the app: **Settings → Daily backup to GitHub**. Paste the link (`https://github.com/<name>/shop-billing-data`),
   click **Save link**, then **Export & push now**.
4. The result line must say *Pushed … to GitHub*. Each run is also logged in **Activity History** (*Data exported* or
   *Data export failed*).

**Notes:**

- The repository **must be private**. It contains customers' names, phone numbers, GSTINs and balances.
- Commits use the git name and email already set on the computer. If none is set, they use "Shop Billing".
- If no link is set, the files are still written to `data-export\` but are not pushed.
- To change the times, use `EXPORT_CRON` in `db.properties` (see [section 10](#10-optional-settings-file-dbproperties)).
- **Check it once a week:** look at the "Last export" line in Settings, or open the repository on GitHub and look at
  the latest commit date.

## 7. Restoring data

Use this after a mistake, a broken disk, or to move the data to another computer.

1. **Close the app** (the black window). `restore.bat` refuses to run while the app is open.
2. Pick the backup to restore:
   - from the `backups\` folder (`shop_billing_….zip`), or
   - from GitHub: open the repository → `database/shop-billing-database.zip` → *Download*. For an older day, open
     **History** on that file and download an earlier version.
3. **Drag the zip onto `restore.bat`**. Or double-click `restore.bat` and type the file name.
4. Press **Y** to confirm. The current `data` folder is renamed to `data_before_restore_<date-time>`, so nothing is
   lost, and the backup is unpacked into a new `data` folder.
5. Start the app with `start.bat`.

If the zip doesn't contain a database, `restore.bat` puts the old data back and stops.

## 8. Moving to a new computer

1. Install Java (and git, for the GitHub backup) on the new computer.
2. Copy the whole `shop-billing` folder. If the old computer still works, copying `data\` is the restore.
3. If the old computer is broken, restore `database/shop-billing-database.zip` from GitHub as in [section 7](#7-restoring-data).
4. Start the app, check the latest bills, then run `autostart-on.bat` and set up the GitHub backup again.

## 9. Updating the app

1. Close the app.
2. Replace `target\shop-billing-1.0.0.jar` with the new one. Or build it from the source with
   `mvn -DskipTests package`.
3. Start the app. The data in `data\` is kept, and new fields are added to the database automatically.

Take a **Backup now** first, just in case.

## 10. Optional settings file (db.properties)

Copy `db.properties.example` to `db.properties` in the app folder and remove the `#` in front of the settings you
want:

| Setting | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | Web port. Change it if another program uses 8080; then open `http://localhost:<port>`. `start.bat` reads it too. |
| `EXPORT_CRON` | `0 0 13,20 * * *` | When the GitHub backup runs, as *second minute hour day month weekday*. For example `0 30 19 * * *` means 7:30 PM daily. |

## 11. Using it from other computers in the shop

Other computers or phones on the **same Wi-Fi or network** can open `http://<shop-PC-IP>:8080`. The IP is shown by
`ipconfig` on the shop PC. Allow port 8080 in Windows Firewall when Windows asks, or add an inbound rule. Everyone
uses the same login.

The app is meant for the shop PC only; it is not put on the internet.

## 12. Forgotten password

1. **Close the app.**
2. Open Command Prompt in the app folder and run this as one line:

   ```
   java -cp target\shop-billing-1.0.0.jar -Dloader.main=org.h2.tools.Shell org.springframework.boot.loader.launch.PropertiesLauncher -url jdbc:h2:file:./data/billingdb -user sa -sql "UPDATE SHOP_SETTING SET PASSWORD_HASH='240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9' WHERE ID=1"
   ```

   It should print `(Update count: 1, …)`.
3. Start the app and log in with **admin123**. The username is unchanged, normally `admin`. The app then asks for a
   new password.

The long value in the command is the stored form of `admin123`. The app upgrades it to BCrypt when you log in.

## 13. Security notes

- Passwords are stored as BCrypt hashes. The default password must be changed at the first login.
- Every page needs a login. Sessions end after 8 hours without activity.
- Forms submitted from other websites are blocked (cross-site request protection).
- There is **no limit on wrong password attempts**, as chosen for this shop. Use a password that isn't easy to guess.
- There is **one login** for everyone who uses the app.
- The data file is not encrypted. Keep the computer under a Windows password, and keep the GitHub repository private.

## 14. Troubleshooting

| Problem | Fix |
|---|---|
| Browser says the site can't be reached | Is the black window open? The app takes 30–60 seconds (longer on a slow PC) to start. Then reload. |
| `Port 8080 was already in use` | The app is already running, or another program uses 8080. Close it, or set `PORT=8081` in `db.properties`. |
| `Java is not installed` (from `start.bat`) | Install Java 17+ from <https://adoptium.net>. |
| `Could not find shop-billing-1.0.0.jar` | Build it with `mvn -DskipTests package`, or put the jar next to `start.bat`. |
| Login peacock does not move | The browser has no WebGL (graphics). The still logo is shown and login still works. Update the browser or graphics driver. |
| Peacock animation stutters | Close other heavy programs. The animation is already tuned for basic graphics chips. |
| Stock didn't change after a bill | The bill line name doesn't match an item name. Use Adjust stock, and pick items from the list next time. |
| Receipt prints too small / cut off | In the print window, set the right printer and paper size, Scale 100 %, and Margins *None* or *Default*. |
| *Export failed: git clone / git push failed* | Check that git is installed and signed in, and that the link is right and the repository private. Run `git push` in any repository to sign in again. |
| *Export failed: git commit failed* | Set a git name and email: `git config --global user.name "…"` and `git config --global user.email "…"`. |
| `restore.bat` says the app is still running | Close the black window and try again. |
| "Page not found" | The address or record doesn't exist. Use **Go to Dashboard**. |
