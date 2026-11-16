# User guide

For everyone who makes bills and looks after stock and money at the shop. Every step uses the words shown on the
screen.

**Contents:**

1. [Starting and logging in](#1-starting-and-logging-in)
2. [The screen and the menu](#2-the-screen-and-the-menu)
3. [Local bill (retail counter receipt)](#3-local-bill-retail-counter-receipt)
4. [Wholesale bill (GST invoice)](#4-wholesale-bill-gst-invoice)
5. [Finding, reprinting, editing and cancelling bills](#5-finding-reprinting-editing-and-cancelling-bills)
6. [Customers and money they owe](#6-customers-and-money-they-owe)
7. [Items and stock](#7-items-and-stock)
8. [Purchases and suppliers](#8-purchases-and-suppliers)
9. [Returns](#9-returns)
10. [Expenses](#10-expenses)
11. [Dashboard](#11-dashboard)
12. [Accounts & Profit](#12-accounts--profit)
13. [Reports](#13-reports)
14. [Activity History](#14-activity-history)
15. [End of the day](#15-end-of-the-day)
16. [Golden rules](#16-golden-rules)
17. [Messages you may see](#17-messages-you-may-see)

---

## 1. Starting and logging in

1. Switch on the computer. If the app is set to start by itself, a black window opens and, after about a minute, the
   browser shows the login page. Otherwise double-click **start.bat** in the app folder.
2. The peacock from the shop logo flies in and settles on the logo. It keeps moving while you log in.
3. Type the **Username** and **Password** and click **Login**.
   - If they are right, the button says **Welcome!**, the peacock spreads its wings and flies away, and the
     **Dashboard** opens.
   - If they are wrong, a red message *Invalid username or password* appears and the card shakes. Type them again.
4. The black window is the app itself. **Do not close it while working.** You may minimise it.

You stay logged in for 8 hours of inactivity. To leave, click **Logout** at the bottom of the menu.

> The very first time, the login is `admin` / `admin123`. The app then asks for a new password before anything else.
> See the [Admin guide](admin-guide.md#2-first-time-setup).

## 2. The screen and the menu

On a computer the menu is on the left. On a phone it is at the top: the two bill buttons are always shown, and
**☰ Menu** opens the rest.

| Menu | What it is for |
|---|---|
| **+ Local Bill** (gold) | New retail receipt |
| **+ Wholesale Bill** | New GST invoice |
| **Dashboard** | Today's sales, charts, low stock, best sellers, latest bills |
| **Bills → Local Bills** | All local bills: search, reprint, cancel, restore |
| **Bills → Wholesale Bills** | All GST bills: search, open, print |
| **Bills → Returns** | Goods brought back by customers |
| **Stock → Items** | Item list, prices, stock, photos. A red number means items are low in stock. |
| **Stock → Purchases** | Goods bought from suppliers |
| **Stock → Suppliers** | Supplier details and what you owe them |
| **Money → Customers** | Customer details, their bills and payments |
| **Money → Payment Reminders** | Everyone who owes money, with a WhatsApp reminder button |
| **Money → Expenses** | Rent, electricity, transport, salary… |
| **Money → Accounts & Profit** | Money in and out, profit and loss |
| **More → Reports** | Sales, best sellers, purchases, stock value, profit, outstanding |
| **More → Activity History** | A record of important changes |
| **More → Settings** | Shop details, receipt text, logo, password, backups |

Green messages at the top of a page mean the action worked. Red messages mean something must be corrected.

## 3. Local bill (retail counter receipt)

Use this for walk-in customers. It prints a small receipt with a UPI QR code.

1. Click **+ Local Bill**. The **Bill No** shows the next number (for example `0012/2026`) and the **Date** is
   today.
2. *(Optional)* **Customer Name** and **Cust Mobile No.**
   - Leave the name empty for a cash customer; the receipt shows *Cash*.
   - Picking a saved customer fills in the mobile number. A new name with a mobile number is saved as a customer
     automatically.
3. **Payment:** choose **Cash**, **UPI** or **Bank transfer**.
4. **Items:** in the **Item** box start typing and pick the item from the list. **MRP** and **Rate** fill in from the
   item list. You can change them.
   - Type the **Qty**.
   - Pressing **Enter** in the Rate box moves to the next row (a new row is added if needed).
   - **+ Add Item Row** adds an empty row; **X** removes a row.
5. The totals update as you type:
   - **Total Sale:** the sum of Qty × Rate.
   - **Total Savings:** MRP total minus the sale. This is what the customer saves.
   - **Net Payable (₹):** what the customer pays.
6. Click **Save & Print Bill**. The bill is saved, then the print window opens showing only the receipt. Choose the
   receipt printer and print.
7. Click **+ New Bill** for the next customer.

**What the receipt shows:** logo, shop name and address, bill number, date and time, customer, items (Qty, MRP, Rate,
Amount), totals, the amount in words, the savings, and a **UPI QR code for the exact amount**. Scanning it with any
UPI app fills in your UPI ID and the bill amount. The text at the top and bottom comes from **Settings → Local Bill**.

**Stock:** every item on the bill is taken out of stock, if its name matches an item in **Items**.

**Saved Bills:** under the bill, **Saved Bills → Bill No → Load** opens an earlier bill. Printing it again updates
*that* bill; it does not create a new number.

**Daily Sales Report:** under Saved Bills, choose a **Date** and click **Show** to see that day's local bills and
totals.

## 4. Wholesale bill (GST invoice)

Use this for shops, dealers and anyone who needs a GST invoice.

1. Click **+ Wholesale Bill**. The top shows the number the bill will get.
2. **Customer Name (M/s.):** start typing and pick a saved customer. Address, GSTIN and phone fill in. A new name is
   saved as a customer when the bill is saved.
3. **Bill Date** is today; change it if needed.
4. **Bill Type:**
   - **Cash:** paid now. The bill is marked *Paid*.
   - **Credit:** paid later. The bill stays *Unpaid* and the amount appears under money owed.
5. **Payment:** Cash / UPI / Bank transfer.
6. *(Optional)* **Address / Place**, **Party GSTIN** (exactly 15 characters), **Phone**, **By** (salesman / agent).
7. **Items:** fill in one row per item.
   - **Particulars:** start typing and pick the item. The **wholesale price** fills the Rate.
   - **Details** *(optional)*: an extra line, e.g. *100 colour + 10 black*.
   - **HSN**, **Piece** (quantity), **Metter** (unit, e.g. *7 Part*), **Rate**. **Amount** is calculated.
   - **+ Add Item Row (Alt+A)** adds a row. Pressing Enter in the last Rate box also adds one. **✕** removes a row.
8. **Tax:**
   - **Tax Type:** *CGST + SGST* (customer in the same state), *IGST* (other state) or *No GST*.
   - **GST Rate (%)**: 5 by default. The suggested rates are 0, 5, 12, 18 and 28.
   - **Discount (₹)** *(optional)*: it cannot be more than the sub total.
   - **Notes** *(optional)*.
9. The totals on the right update as you type: **Sub Total**, **Discount**, **Taxable Amount**, the tax (CGST + SGST
   at half the rate each, or IGST at the full rate), **Round Off** (to the nearest rupee) and **Grand Total**.
10. Click **Save & Print Bill**. Or click **Save only (no print)**.

**Printing a wholesale bill:**

1. On the bill page, click **Print Bill** for A4.
2. In the print window, choose **A4** or **A5**, and **Original** or **Duplicate**. *Duplicate* adds a "DUPLICATE COPY"
   mark.
3. Click **Print / Save as PDF**. Choose the paper size that matches (A4 or A5).
4. To make a PDF, choose **Save as PDF** as the printer.

**Stock:** every item on the bill is taken out of stock, if its name matches an item in **Items**.

## 5. Finding, reprinting, editing and cancelling bills

**Local Bills** and **Wholesale Bills** list all bills, newest first. You can search by bill number, customer or
mobile, between two dates, and wholesale bills also by status (Paid / Unpaid / Cancelled). The total at the bottom
leaves out cancelled bills.

Each row has buttons:

| Button | What it does |
|---|---|
| **Print** | Opens the print view |
| **Open** | Shows the bill |
| **Cancel** | Cancels the bill (asks to confirm). Its items go back into stock. The bill stays in the list, crossed out. |
| **Restore** | Undoes a cancel. The items are taken out of stock again. |

On an open **wholesale** bill:

- **Edit Bill:** change anything. Stock is corrected by the difference. A cancelled bill must be restored first.
- **Copy to New Bill:** starts a new bill with the same customer and items.
- **Mark as Paid / Mark as Unpaid** for credit bills. *Mark as Paid* records the remaining amount as received today.
- **Cancel Bill / Restore Bill.**

The **Dashboard** also lists the **5 latest bills** (local and wholesale together), each with **Print** and **Open**.

## 6. Customers and money they owe

**Customers** lists all customers, their type (Retail / Wholesale) and how much each owes.

- **+ Add Customer**, **Edit**, **Delete**. Deleting a customer keeps their bills.
- **Open** shows the customer's wholesale bills, local bills and payments received.

**Receiving money for credit bills:**

1. Open the customer and use **Receive payment**: enter the **amount**, **mode** (Cash/UPI/Bank), **date** and an
   optional **note**, then click **Save payment**.
2. The money is set against the **oldest unpaid credit bill first**. When a bill is fully paid, it becomes *Paid*.
3. You cannot receive more than the customer owes. The app shows the outstanding amount and refuses the payment.

**Payment Reminders** lists everyone who owes money, the largest amount first, with how many days it has been due.
Bills more than 30 days old are marked *Over 30*.

- **WhatsApp reminder** opens WhatsApp with a polite message ready to send. It includes the amount, the date and your
  UPI ID.
- **Receive Payment** opens that customer.

## 7. Items and stock

**Items** lists every item with its prices, stock, bundles and stock value.

- **Search** finds items by name, code, category, fabric, colour or barcode.
- The **Low stock** filter shows items at or below their minimum.

**Adding or editing an item** (**+ Add Item** / **Edit**):

- **Item details:** name (must be unique), code, category, saree type, fabric, colour, design, barcode, HSN, unit.
- **Prices:**
  - **Purchase price:** what you paid. It is used for stock value and profit.
  - **Selling price:** used on local bills.
  - **Wholesale price:** used on wholesale bills.
  - **MRP.**
- **Stock:**
  - **Opening stock:** entered only when the item is created.
  - **Minimum stock:** low-stock alert level.
  - **Pieces per bundle:** shows stock as e.g. *2 bundles + 4 pcs*.
- **Photo:** PNG, JPG, WEBP or GIF, under 1 MB.

**How stock changes by itself:**

| Event | Stock |
|---|---|
| Local or wholesale bill saved | goes **down** |
| Bill cancelled | goes **back up** |
| Bill restored | goes down again |
| Bill edited | changes by the difference |
| Purchase saved | goes **up** |
| Purchase deleted | goes down again |
| Return recorded | goes **up** |

> Stock only moves when the name on the bill **matches an item name** (capital letters do not matter). Always pick
> the item from the list instead of typing a different spelling.

**Adjust stock** (on the item's edit page) corrects the count by hand, e.g. after counting or for damaged goods.
Enter `+10` or `-2` and a **reason**; the reason is required and is saved in Activity History. Editing an item never
changes its stock; only Adjust stock does.

**Low stock:** an item is low when its stock is at or below its minimum stock. The menu shows a red number, and the
Dashboard lists the items.

## 8. Purchases and suppliers

**Suppliers** lists each supplier and the **balance to pay**: total purchases minus payments made.

- **+ Add Supplier:** name (required), phone, address, GSTIN (15 characters, or leave empty), notes (bank details,
  agent…).
- **Open** shows the supplier's purchases and payments, and has **Record a payment to this supplier** (amount, mode,
  date, note).
- A supplier with purchases or payments cannot be deleted.

**Purchases → + Purchase** records goods bought:

1. Choose the **supplier**, then enter the **invoice no**, **date** and **other charges** (transport etc.).
2. Add one line per item: **item name** (pick from the list), **quantity**, **rate**.
3. Save. The stock of each item goes up, and the total is added to what you owe the supplier.

Editing a purchase corrects the stock by the difference. Deleting it takes the stock back out.

## 9. Returns

**Returns** records goods a customer brings back:

- Enter the **date**, the bill number (optional), the customer (optional), the **item**, the **quantity**, the
  **refund** (0 if exchanged), the **mode**, and a **reason**.
- Saving puts the quantity **back into stock**. The refund is counted as money paid out, and it is taken off sales in
  the profit.
- **Delete** undoes a return and takes the stock out again.

## 10. Expenses

**Expenses → Add expense:**

1. Enter the date and choose a type: *Rent, Electricity, Transport, Salary, Tea / Snacks, Packing, Repairs or Other*.
2. Enter the amount and choose the mode (Cash/UPI/Bank). The note is optional.
3. Click **Save**.

The list shows expenses by date with totals. **Delete** removes one; the deletion is saved in Activity History.

## 11. Dashboard

- **Banner:** the shop name on the left, and Lakshmi with glowing lamps on the right. Rose petals fall over it.
- **Today – Total:** today's sales and bill count, split into **Local** and **Wholesale**. Click either part to open
  that list.
- **To Collect / To Pay:**
  - what customers owe (credit bills); **Customers owe us →** opens Payment Reminders
  - what you owe suppliers
- **Pie charts for this month:**
  - **Sales this month:** local vs wholesale.
  - **Money received:** by Cash / UPI / Bank.
  - **Sales by category.**
  - **Expenses** by type.

  Point at a slice to see its value.
- **Low-stock alerts** and **Best sellers this month.**
- **Recent Bills:** the 5 latest bills, each with **Print** and **Open**.

## 12. Accounts & Profit

Choose a date range at the top. The page shows:

- **Profit & Loss:** sales (wholesale before GST, plus local), minus returns, minus the cost of the goods sold (at
  each item's *purchase price*), gives **gross profit**. Minus expenses gives **net profit**. Items without a purchase
  price are counted at zero cost; the page warns about them.
- **Cash & digital:** money in and money out, by Cash / UPI / Bank.
  - **In:** cash-type wholesale bills, local bills, and payments received.
  - **Out:** expenses, supplier payments and refunds.
- **Expenses by type** and a **Daily income & expense** table.

## 13. Reports

Choose the dates, then a tab:

| Tab | Shows |
|---|---|
| **Sales (bills)** | Local bills and wholesale (GST) bills in the period, with totals |
| **Daily / Monthly / Yearly** | Bills, wholesale, local, money received, expenses and paid out, per day, month or year |
| **Best Sellers** | Best-selling items, and sales by category and by fabric |
| **Purchases** | Purchases in the period |
| **Stock Value** | Every item's stock and value (at purchase price) |
| **Profit & Loss** | Same as Accounts & Profit |
| **Outstanding** | Customers who owe us, and suppliers we owe |

Each tab has **⬇ Excel**, which downloads a CSV file that opens in Excel (the Sales tab has **⬇ Local Excel** and
**⬇ Wholesale Excel**), and **Print / PDF**.

## 14. Activity History

A record of important actions, with date, time and user. It records:

- items added, deleted or with price changes
- stock adjustments
- bills cancelled, restored, or marked paid / unpaid
- payments received or made, and payments deleted
- purchases added, edited or deleted
- goods returned, and returns deleted
- customers, suppliers and expenses deleted
- changes to the GitHub backup link
- data exports

It cannot be edited.

## 15. End of the day

- Nothing special is needed. Every bill is saved the moment it is made.
- **Backups run by themselves:** one copy a day on the computer, and two a day to GitHub (1 PM and 8 PM).
- Close the browser and the black window, then shut down the computer.

## 16. Golden rules

1. **Do not close the black window while billing.**
2. **Always pick items from the list.** A different spelling does not reduce stock.
3. **Never delete or move the app folder**, or the `data` and `backups` folders inside it.
4. Use **Cancel** for a wrong bill. Do not try to "fix" it with a second bill.
5. If something looks wrong, **do not guess. Call the person who looks after the app.**

## 17. Messages you may see

| Message | Meaning / what to do |
|---|---|
| *Invalid username or password* | Wrong login. Check Caps Lock and type again. |
| *Customer name is required* | Fill in the customer name on a wholesale bill. |
| *Add at least one item to the bill* | The bill has no items. |
| *Quantity must be more than 0 for "…"* | Enter a quantity for that item. |
| *Discount cannot be more than the sub total* | Lower the discount. |
| *GSTIN must be exactly 15 characters* | Correct the GSTIN, or leave it empty. |
| *A cancelled bill cannot be edited. Restore it first.* | Click **Restore Bill**, then **Edit Bill**. |
| *Amount is more than the outstanding ₹…* | The customer owes less than you entered. |
| *Write a reason for the stock change* | Adjust stock needs a reason. |
| *Another item already has this name* | Item names must be unique. |
| *Page not found* | Click **Go to Dashboard**. |
