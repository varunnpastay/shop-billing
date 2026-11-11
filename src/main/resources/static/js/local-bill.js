// Local (retail) billing – the Smart Billing page, now saving bills to the server.
let qrInstance = null;
let currentTime = null;      // time of a saved bill; null = show the current time
let cancelled = false;

const root = document.querySelector(".sb");
const cfg = root.dataset;

function $(id) {
  return document.getElementById(id);
}

function showMsg(text, kind) {
  const el = $("sb-msg");
  if (!text) {
    el.hidden = true;
    return;
  }
  el.textContent = text;
  el.className = "sb-msg " + (kind || "err");
  el.hidden = false;
}

function num(v) {
  return parseFloat(v) || 0;
}

// Today's date as yyyy-mm-dd in the shop's local time (valueAsDate uses UTC, which is yesterday before 5:30 AM IST).
function todayIso() {
  const d = new Date();
  return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
}

// 3 -> "3", 2.5 -> "2.50" (metres of cloth)
function fmtQty(q) {
  return Number.isInteger(q) ? String(q) : q.toFixed(2);
}

// yyyy-mm-dd -> dd-mm-yyyy for the printed receipt
function displayDate(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso || "");
  return m ? `${m[3]}-${m[2]}-${m[1]}` : iso || "";
}

// ---------------- item rows ----------------

function addItemRow(name = "", qty = 1, mrp = 0, rate = 0) {
  const tbody = $("items-input-body");
  const tr = document.createElement("tr");
  tr.innerHTML = `
    <td><input type="text" class="item-name" list="product-list"/></td>
    <td><input type="number" class="item-qty" min="0" step="any"/></td>
    <td><input type="number" class="item-mrp" min="0" step="0.01"/></td>
    <td><input type="number" class="item-rate" min="0" step="0.01"/></td>
    <td><button type="button" class="remove-row">X</button></td>
  `;
  tr.querySelector(".item-name").value = name;
  tr.querySelector(".item-qty").value = qty;
  tr.querySelector(".item-mrp").value = mrp;
  tr.querySelector(".item-rate").value = rate;
  tbody.appendChild(tr);

  tr.querySelector(".remove-row").addEventListener("click", () => {
    tr.remove();
    refreshBill();
  });

  ["item-qty", "item-rate", "item-mrp"].forEach((cls) => {
    tr.querySelector("." + cls).addEventListener("input", () => refreshBill());
  });

  // Enter on the last field of a row jumps to the next row (adds one if needed) – fast counter entry
  tr.querySelector(".item-rate").addEventListener("keydown", (e) => {
    if (e.key !== "Enter") return;
    e.preventDefault();
    let next = tr.nextElementSibling;
    if (!next) next = addItemRow();
    next.querySelector(".item-name").focus();
  });

  // picking a saved item fills its MRP and rate
  const nameInput = tr.querySelector(".item-name");
  nameInput.addEventListener("input", () => {
    const opt = findOption("product-list", nameInput.value);
    if (opt) {
      if (opt.dataset.mrp) tr.querySelector(".item-mrp").value = opt.dataset.mrp;
      if (opt.dataset.rate) tr.querySelector(".item-rate").value = opt.dataset.rate;
    }
    refreshBill();
  });
  return tr;
}

function findOption(listId, value) {
  const v = (value || "").trim().toLowerCase();
  if (!v) return null;
  return Array.from(document.querySelectorAll("#" + listId + " option")).find(
    (o) => o.value.toLowerCase() === v
  );
}

function getItemsFromForm() {
  return Array.from(document.querySelectorAll("#items-input-body tr")).map((row) => {
    const name = row.querySelector(".item-name").value || "";
    const qty = num(row.querySelector(".item-qty").value);
    const mrp = num(row.querySelector(".item-mrp").value);
    const rate = num(row.querySelector(".item-rate").value);
    return { name, qty, mrp, rate, amt: qty * rate };
  });
}

// ---------------- totals & preview ----------------

function refreshBill() {
  updateTotalsFromForm();
  updatePreview();
}

// leave-page warning when an unsaved bill has items
window.addEventListener("beforeunload", (e) => {
  const hasItems = getItemsFromForm().some((i) => i.name.trim());
  if (hasItems && !$("billId").value) {
    e.preventDefault();
    e.returnValue = "";
  }
});

function updateTotalsFromForm() {
  const items = getItemsFromForm();
  const totalSale = items.reduce((sum, item) => sum + item.amt, 0);
  const totalMrp = items.reduce((sum, item) => sum + item.mrp * item.qty, 0);
  $("totalSale").value = totalSale.toFixed(2);
  $("totalSavings").value = Math.max(totalMrp - totalSale, 0).toFixed(2);
  $("netPayable").value = totalSale.toFixed(2); // customer pays the RATE total
}

function numberToWordsIndian(num) {
  if (num === 0) return "Zero";
  const a = ["", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
    "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"];
  const b = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];

  function inWords(n) {
    if (n > 19) return (b[Math.floor(n / 10)] + (n % 10 ? " " + a[n % 10] : "")).trim();
    return a[n];
  }
  function sectionToWords(n, label) {
    if (!n) return "";
    return (inWords(n) + " " + label + " ").trim() + " ";
  }

  let result = "";
  let n = Math.floor(num);
  result += sectionToWords(Math.floor(n / 10000000), "Crore");
  n = n % 10000000;
  result += sectionToWords(Math.floor(n / 100000), "Lakh");
  n = n % 100000;
  result += sectionToWords(Math.floor(n / 1000), "Thousand");
  n = n % 1000;
  result += sectionToWords(Math.floor(n / 100), "Hundred");
  n = n % 100;
  if (n > 0) result += (result ? "and " : "") + inWords(n);
  return result.trim();
}

function updatePreview() {
  const billNo = $("billNo").value;
  $("meta-billNo").textContent = billNo;
  $("bill-no-chip").textContent = billNo ? ($("billId").value ? "Bill " : "Next: ") + billNo : "";
  $("meta-billDate").textContent = displayDate($("billDate").value);
  $("meta-customerName").textContent = $("customerName").value || "Cash";
  $("meta-custMobile").textContent = $("custMobile").value;

  const tbody = $("bill-items-body");
  tbody.innerHTML = "";
  let totalQty = 0;
  let totalSale = 0;
  getItemsFromForm().forEach((item) => {
    if (!item.name && !item.amt) return;
    const tr = document.createElement("tr");
    [
      [item.name, ""],
      [String(item.qty), "col-qty"],
      [item.mrp.toFixed(2), "col-mrp"],
      [item.rate.toFixed(2), "col-rate"],
      [item.amt.toFixed(2), "col-amt"],
    ].forEach(([text, cls]) => {
      const td = document.createElement("td");
      td.textContent = text;
      if (cls) td.className = cls;
      tr.appendChild(td);
    });
    tbody.appendChild(tr);
    totalQty += item.qty;
    totalSale += item.amt;
  });

  $("bill-total-qty").textContent = fmtQty(totalQty);
  $("bill-total-sale").textContent = totalSale.toFixed(2);

  const netPayable = num($("netPayable").value);
  $("amount-in-words").textContent = numberToWordsIndian(Math.round(netPayable));
  $("summary-total-sale").textContent = num($("totalSale").value).toFixed(2);
  $("summary-total-savings").textContent = num($("totalSavings").value).toFixed(2);
  $("summary-net-payable").textContent = netPayable.toFixed(2);
  $("summary-time").textContent =
    currentTime || new Date().toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" });
  $("bill-cancelled").hidden = !cancelled;

  // UPI payment QR for the bill amount
  if (qrInstance && cfg.upi) {
    qrInstance.clear();
    const tn = encodeURIComponent(`${cfg.shopName || ""} Bill ${billNo || ""}`.trim());
    const upiUrl = `upi://pay?pa=${encodeURIComponent(cfg.upi)}&cu=INR&am=${encodeURIComponent(
      netPayable.toFixed(2)
    )}&tn=${tn}`;
    qrInstance.makeCode(upiUrl);
  }
}

// ---------------- load / save ----------------

function getBillDataFromForm() {
  const id = $("billId").value;
  return {
    id: id ? Number(id) : null,
    billDate: $("billDate").value || null,
    customerName: $("customerName").value,
    custMobile: $("custMobile").value,
    items: getItemsFromForm()
      .filter((i) => i.name.trim())
      .map((i) => ({ name: i.name, qty: i.qty, mrp: i.mrp, rate: i.rate })),
  };
}

function setFormFromBillData(data) {
  if (!data) return;
  $("billId").value = data.id || "";
  $("billNo").value = data.billNo || "";
  $("billDate").value = data.billDate || "";
  $("customerName").value = data.customerName || "";
  $("custMobile").value = data.custMobile || "";
  currentTime = data.time || null;
  cancelled = !!data.cancelled;

  const tbody = $("items-input-body");
  tbody.innerHTML = "";
  if (Array.isArray(data.items) && data.items.length) {
    data.items.forEach((item) => addItemRow(item.name, Number(item.qty), Number(item.mrp), Number(item.rate)));
  } else {
    addItemRow();
  }
  $("print-btn").disabled = cancelled;
  showMsg(cancelled ? "This bill is cancelled. Restore it from the local bills list to print it." : "");
  refreshBill();
}

function newBill() {
  $("billId").value = "";
  $("billNo").value = cfg.nextBillNo || "";
  $("billDate").value = todayIso();
  $("customerName").value = "";
  $("custMobile").value = "";
  currentTime = null;
  cancelled = false;
  $("print-btn").disabled = false;
  $("items-input-body").innerHTML = "";
  addItemRow();
  showMsg("");
  history.replaceState(null, "", cfg.openUrl + "/new");
  refreshBill();
  // the number shown is a preview; the real one is given when the bill is saved
  fetchJson(cfg.apiUrl + "/next")
    .then((r) => {
      if (!$("billId").value) {
        $("billNo").value = r.billNo;
        refreshBill();
      }
    })
    .catch(() => {});
}

async function fetchJson(url, options) {
  const res = await fetch(url, Object.assign({ credentials: "same-origin" }, options || {}));
  if (res.redirected && res.url.includes("/login")) {
    throw new Error("Your session has ended. Please log in again.");
  }
  const body = await res.json().catch(() => null);
  if (!res.ok) throw new Error((body && body.error) || "Could not reach the server (" + res.status + ")");
  return body;
}

async function loadBill(id) {
  try {
    setFormFromBillData(await fetchJson(cfg.apiUrl + "/bills/" + id));
    history.replaceState(null, "", cfg.openUrl + "/" + id);
    return true;
  } catch (e) {
    showMsg(e.message);
    return false;
  }
}

async function saveCurrentBill() {
  const data = getBillDataFromForm();
  if (!data.items.length) throw new Error("Add at least one item to the bill");
  const saved = await fetchJson(cfg.saveUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  $("billId").value = saved.id;
  $("billNo").value = saved.billNo;
  currentTime = saved.time || currentTime;
  history.replaceState(null, "", cfg.openUrl + "/" + saved.id);
  await populateSavedBillsDropdown(saved.id);
  return saved;
}

async function populateSavedBillsDropdown(selectedId) {
  const select = $("savedBills");
  try {
    const list = await fetchJson(cfg.apiUrl + "/saved");
    select.innerHTML = '<option value="">-- Select --</option>';
    list.forEach((b) => {
      const option = document.createElement("option");
      option.value = b.id;
      option.textContent = b.billNo;
      if (selectedId && String(b.id) === String(selectedId)) option.selected = true;
      select.appendChild(option);
    });
  } catch (e) {
    /* dropdown is optional */
  }
}

// ---------------- daily report ----------------

async function renderDailyReport(dateStr) {
  const summaryEl = $("report-summary");
  const billsEl = $("report-bills");
  billsEl.innerHTML = "";
  if (!dateStr) {
    summaryEl.textContent = "Select a date to see report.";
    return;
  }
  let bills;
  try {
    bills = await fetchJson(cfg.apiUrl + "/report?date=" + encodeURIComponent(dateStr));
  } catch (e) {
    summaryEl.textContent = e.message;
    return;
  }
  if (!bills.length) {
    summaryEl.textContent = "No bills found for this date.";
    return;
  }
  const totalSale = bills.reduce((s, b) => s + Number(b.totalSale || 0), 0);
  const totalSavings = bills.reduce((s, b) => s + Number(b.totalSavings || 0), 0);
  const netPayable = bills.reduce((s, b) => s + Number(b.netPayable || 0), 0);
  summaryEl.textContent = `Bills: ${bills.length} | Total Sale: ${totalSale.toFixed(2)} | Total Savings: ${totalSavings.toFixed(
    2
  )} | Net Payable: ${netPayable.toFixed(2)}`;

  const table = document.createElement("table");
  table.className = "items-input";
  table.innerHTML =
    "<thead><tr><th>Bill No</th><th>Customer</th><th>Total Sale</th><th>Total Savings</th><th>Net Payable</th></tr></thead>";
  const tbody = document.createElement("tbody");
  bills.forEach((b) => {
    const tr = document.createElement("tr");
    [b.billNo, b.customerName || "", Number(b.totalSale || 0).toFixed(2), Number(b.totalSavings || 0).toFixed(2),
      Number(b.netPayable || 0).toFixed(2)].forEach((text) => {
      const td = document.createElement("td");
      td.textContent = text;
      tr.appendChild(td);
    });
    tbody.appendChild(tr);
  });
  table.appendChild(tbody);
  billsEl.appendChild(table);
}

// ---------------- start ----------------

async function init() {
  $("add-row-btn").addEventListener("click", () => addItemRow());
  $("new-bill-btn").addEventListener("click", () => newBill());

  $("print-btn").addEventListener("click", async () => {
    const btn = $("print-btn");
    btn.disabled = true;
    try {
      updateTotalsFromForm();
      await saveCurrentBill();
      showMsg("");
      updatePreview();
      window.print();
    } catch (e) {
      showMsg(e.message);
    } finally {
      btn.disabled = cancelled;
    }
  });

  $("load-bill-btn").addEventListener("click", () => {
    const id = $("savedBills").value;
    if (id) loadBill(id);
  });

  const reportDate = $("reportDate");
  reportDate.value = todayIso();
  $("show-report-btn").addEventListener("click", () => renderDailyReport(reportDate.value));

  ["billNo", "custMobile"].forEach((id) => $(id).addEventListener("input", () => refreshBill()));
  $("customerName").addEventListener("input", () => {
    const opt = findOption("customer-list", $("customerName").value);
    if (opt && opt.dataset.phone && !$("custMobile").value) $("custMobile").value = opt.dataset.phone;
    refreshBill();
  });
  $("billDate").addEventListener("change", () => refreshBill());

  const qrElement = $("qrcode");
  if (qrElement && window.QRCode && cfg.upi) {
    qrInstance = new QRCode(qrElement, { width: 80, height: 80 });
  }

  populateSavedBillsDropdown(cfg.billId);

  if (cfg.billId) {
    const ok = await loadBill(cfg.billId);
    if (ok && cfg.autoPrint === "true" && !cancelled) {
      setTimeout(() => window.print(), 300); // let the QR code draw first
    }
  } else {
    newBill();
  }
}

document.addEventListener("DOMContentLoaded", init);
