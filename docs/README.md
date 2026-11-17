# Shop Billing – Documentation

Billing software for **Shri Vijaylaxmi Textiles**. It runs on the shop computer and opens in the web browser. It needs
no internet, except for the daily backup to GitHub.

| Guide | For | What it covers |
|---|---|---|
| [User guide](user-guide.md) | Anyone billing at the shop | Logging in, local and wholesale bills, printing, customers, items and stock, purchases, payments, expenses, returns, reports, the dashboard |
| [Admin guide](admin-guide.md) | The person who looks after the app | Installing, first-time setup, settings, backups and restore, the daily GitHub backup, starting automatically, updating, password reset, troubleshooting |
| [Developer guide](developer-guide.md) | Anyone changing the code | How the app is built, folders, data model, business rules (GST, stock, payments, profit), the login animation, styles, configuration, web addresses, tests, working on another laptop |

## The app at a glance

- **Two kinds of bill**
  - **Local bill:** the small retail receipt, with a UPI QR code.
  - **Wholesale bill:** the A4/A5 GST tax invoice, paid now (cash) or later (credit).
- **Stock** moves automatically with bills, purchases, cancellations and returns.
- **Money:** payments from customers and to suppliers, expenses, Cash / UPI / Bank totals, and an estimated profit.
- **Reports and dashboard:** day, month and year sales, best sellers, stock value, profit, and who owes what.
- **Safety:** a password login, a history of important changes, a daily backup on the computer, and a twice-daily
  backup to a private GitHub repository.

## Words used in these guides

| Word | Meaning |
|---|---|
| **Local bill** | Retail counter receipt (`+ Local Bill`). Numbered `0001/2026`, `0002/2026`…, starting again each year. |
| **Wholesale bill** | GST tax invoice (`+ Wholesale Bill`). Numbered 1, 2, 3… |
| **Cash bill** | Wholesale bill paid at once. It is marked *Paid* straight away. |
| **Credit bill** | Wholesale bill to be paid later. It stays *Unpaid* until the money is received. |
| **Outstanding / balance** | Money a customer still owes on credit bills, or money the shop still owes a supplier. |
| **Item** | A product in the item list (saree, dress material, …) with prices and stock. |
| **Stock** | How many pieces (or metres) of an item are in the shop. |
| **Backup** | A copy of all the data that can be loaded back with `restore.bat`. |
