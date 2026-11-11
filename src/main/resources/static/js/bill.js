(function () {
    const body = document.getElementById('itemsBody');
    const tpl = document.getElementById('rowTemplate');
    const taxType = document.getElementById('taxType');
    const gstPercent = document.getElementById('gstPercent');
    const discount = document.getElementById('discount');

    const num = v => { const n = parseFloat(v); return isNaN(n) ? 0 : n; };
    const r2 = n => Math.round((n + Number.EPSILON) * 100) / 100;
    const fmt = n => n.toLocaleString('en-IN', {minimumFractionDigits: 2, maximumFractionDigits: 2});

    function reindex() {
        body.querySelectorAll('tr.item-row').forEach((tr, i) => {
            tr.querySelector('.sl').textContent = i + 1;
            tr.querySelectorAll('input[data-f]').forEach(inp => {
                inp.name = 'items[' + i + '].' + inp.dataset.f;
                inp.id = 'items' + i + '.' + inp.dataset.f;
            });
        });
    }

    function addRow(focus) {
        body.appendChild(tpl.content.cloneNode(true));
        reindex();
        if (focus) body.lastElementChild.querySelector('input[data-f=particulars]').focus();
    }

    function calculate() {
        let sub = 0;
        body.querySelectorAll('tr.item-row').forEach(tr => {
            const qty = num(tr.querySelector('[data-f=qty]').value);
            const rate = num(tr.querySelector('[data-f=rate]').value);
            const amt = r2(qty * rate);
            tr.querySelector('.amt').textContent = fmt(amt);
            sub += amt;
        });
        sub = r2(sub);
        const disc = num(discount.value);
        const taxable = r2(sub - disc);
        const gst = num(gstPercent.value);
        let cgst = 0, sgst = 0, igst = 0;
        if (taxType.value === 'CGST_SGST') {
            cgst = sgst = r2(taxable * gst / 200);
        } else if (taxType.value === 'IGST') {
            igst = r2(taxable * gst / 100);
        }
        const total = r2(taxable + cgst + sgst + igst);
        const grand = Math.round(total);

        document.getElementById('tSub').textContent = fmt(sub);
        document.getElementById('tDisc').textContent = fmt(disc);
        document.getElementById('tTaxable').textContent = fmt(taxable);
        document.getElementById('tCgst').textContent = fmt(cgst);
        document.getElementById('tSgst').textContent = fmt(sgst);
        document.getElementById('tIgst').textContent = fmt(igst);
        document.getElementById('tRound').textContent = fmt(r2(grand - total));
        document.getElementById('tGrand').textContent = '₹ ' + fmt(grand);
        document.getElementById('lCgst').textContent = 'CGST ' + (gst / 2) + '%';
        document.getElementById('lSgst').textContent = 'SGST ' + (gst / 2) + '%';
        document.getElementById('lIgst').textContent = 'IGST ' + gst + '%';

        document.querySelectorAll('.cgst-row').forEach(e => e.style.display = taxType.value === 'CGST_SGST' ? '' : 'none');
        document.querySelectorAll('.igst-row').forEach(e => e.style.display = taxType.value === 'IGST' ? '' : 'none');
    }

    // fill item details when a saved item is picked
    body.addEventListener('change', e => {
        if (e.target.dataset.f !== 'particulars') return;
        const p = PRODUCTS.find(x => x.name.toLowerCase() === e.target.value.trim().toLowerCase());
        if (!p) return;
        const tr = e.target.closest('tr');
        if (p.hsn) tr.querySelector('[data-f=hsn]').value = p.hsn;
        if (p.unit) tr.querySelector('[data-f=unit]').value = p.unit;
        if (p.rate != null) tr.querySelector('[data-f=rate]').value = p.rate;
        const qty = tr.querySelector('[data-f=qty]');
        if (!qty.value) qty.value = 1;
        qty.focus();
        calculate();
    });

    // fill customer details when a saved customer is picked
    document.getElementById('customerName').addEventListener('change', e => {
        const c = CUSTOMERS.find(x => x.name.toLowerCase() === e.target.value.trim().toLowerCase());
        if (!c) return;
        document.getElementById('customerAddress').value = c.address || '';
        document.getElementById('customerGstin').value = c.gstin || '';
        document.getElementById('customerPhone').value = c.phone || '';
    });

    body.addEventListener('click', e => {
        if (!e.target.classList.contains('btn-x')) return;
        const rows = body.querySelectorAll('tr.item-row');
        const tr = e.target.closest('tr');
        if (rows.length === 1) {
            tr.querySelectorAll('input').forEach(i => i.value = '');
        } else {
            tr.remove();
        }
        reindex();
        calculate();
    });

    // Enter on rate of the last row adds a new row
    body.addEventListener('keydown', e => {
        if (e.key === 'Enter' && e.target.dataset.f === 'rate' && e.target.closest('tr') === body.lastElementChild) {
            e.preventDefault();
            addRow(true);
        }
    });

    // stop Enter from accidentally submitting the bill
    document.getElementById('billForm').addEventListener('keydown', e => {
        if (e.key === 'Enter' && e.target.tagName === 'INPUT') e.preventDefault();
    });

    document.addEventListener('keydown', e => {
        if (e.altKey && (e.key === 'a' || e.key === 'A')) { e.preventDefault(); addRow(true); }
    });

    document.getElementById('addRow').addEventListener('click', () => addRow(true));
    document.getElementById('billForm').addEventListener('input', calculate);
    taxType.addEventListener('change', calculate);

    document.getElementById('billForm').addEventListener('submit', e => {
        const filled = [...body.querySelectorAll('[data-f=particulars]')].some(i => i.value.trim() !== '');
        if (!filled) { e.preventDefault(); alert('Add at least one item'); }
    });

    reindex();
    calculate();
})();
