package com.smme.billing.service;

import com.smme.billing.model.Bill;
import com.smme.billing.model.BillItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BillCalculationTest {

    private static Bill bill(String taxType, String gst, String discount, String... qtyRate) {
        Bill b = new Bill();
        b.setCustomerName("Test");
        b.setTaxType(taxType);
        b.setGstPercent(new BigDecimal(gst));
        b.setDiscount(new BigDecimal(discount));
        for (int i = 0; i < qtyRate.length; i += 2) {
            BillItem it = new BillItem();
            it.setParticulars("Item " + i);
            it.setQty(new BigDecimal(qtyRate[i]));
            it.setRate(new BigDecimal(qtyRate[i + 1]));
            b.getItems().add(it);
        }
        return b;
    }

    @Test
    void cgstSgstSplitAndRoundOff() {
        Bill b = bill("CGST_SGST", "18", "0", "2", "105.50", "1", "33.33");
        BillService.calculate(b);
        assertEquals(new BigDecimal("244.33"), b.getSubTotal());
        assertEquals(new BigDecimal("21.99"), b.getCgst());
        assertEquals(new BigDecimal("21.99"), b.getSgst());
        assertEquals(new BigDecimal("288.00"), b.getGrandTotal());
        assertEquals(new BigDecimal("-0.31"), b.getRoundOff());
    }

    @Test
    void igstWithDiscount() {
        Bill b = bill("IGST", "12", "100", "10", "50");
        BillService.calculate(b);
        assertEquals(0, new BigDecimal("400").compareTo(b.getTaxableAmount()));
        assertEquals(new BigDecimal("48.00"), b.getIgst());
        assertEquals(BigDecimal.ZERO.setScale(2), b.getCgst().setScale(2));
        assertEquals(new BigDecimal("448.00"), b.getGrandTotal());
    }

    @Test
    void oddGstRateDoesNotThrow() {
        Bill b = bill("CGST_SGST", "5", "0", "1", "33.33");
        assertDoesNotThrow(() -> BillService.calculate(b));
        assertEquals(new BigDecimal("0.83"), b.getCgst());
    }

    @Test
    void validationRejectsBadBills() {
        assertThrows(BillService.InvalidBillException.class, () -> BillService.validate(bill("NONE", "0", "0")));
        assertThrows(BillService.InvalidBillException.class, () -> BillService.validate(bill("NONE", "0", "500", "1", "100")));
        assertThrows(BillService.InvalidBillException.class, () -> BillService.validate(bill("NONE", "0", "0", "0", "100")));
        assertThrows(BillService.InvalidBillException.class, () -> BillService.validate(bill("NONE", "0", "0", "1", "-5")));
        assertThrows(BillService.InvalidBillException.class, () -> BillService.validate(bill("IGST", "150", "0", "1", "5")));
        assertDoesNotThrow(() -> BillService.validate(bill("IGST", "18", "10", "1", "100")));
    }
}
