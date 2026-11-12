package com.smme.billing;

import com.smme.billing.controller.AuthController;
import com.smme.billing.model.Bill;
import com.smme.billing.model.Product;
import com.smme.billing.repository.*;
import com.smme.billing.service.InsightService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Stock, purchases, suppliers, customer payments, expenses, returns, accounts and reports. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:features;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ShopFeaturesTest {

    @Autowired MockMvc mvc;
    @Autowired ProductRepository productRepo;
    @Autowired BillRepository billRepo;
    @Autowired SupplierRepository supplierRepo;
    @Autowired PaymentRepository paymentRepo;
    @Autowired ActivityLogRepository activityRepo;
    @Autowired InsightService insights;

    private BigDecimal stockOf(String name) {
        return productRepo.findFirstByNameIgnoreCase(name).orElseThrow().stock();
    }

    @Test
    void stockMoneyAndReports() throws Exception {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(AuthController.SESSION_USER, "admin");
        String today = LocalDate.now().toString();

        // ---- item with opening stock, prices and minimum stock
        mvc.perform(post("/products/save").session(s)
                        .param("name", "Mysore Silk Saree").param("code", "SVT-1").param("category", "Saree")
                        .param("fabric", "Silk").param("purchasePrice", "1000").param("rate", "1500")
                        .param("wholesalePrice", "1300").param("mrp", "1800").param("stockQty", "20").param("minStock", "5")
                        .param("piecesPerBundle", "10"))
                .andExpect(redirectedUrl("/products"));
        assertEquals(0, new BigDecimal("20").compareTo(stockOf("Mysore Silk Saree")));
        mvc.perform(get("/products").param("q", "svt-1").session(s)).andExpect(content().string(containsString("Mysore Silk Saree")))
                .andExpect(content().string(containsString("2 bundles")));
        mvc.perform(get("/products").param("q", "silk").session(s)).andExpect(content().string(containsString("Mysore Silk Saree")));

        // ---- wholesale credit bill takes stock out
        mvc.perform(post("/bills/save").session(s)
                .param("customerName", "Ravi Textiles").param("billDate", today).param("billType", "CREDIT")
                .param("paymentMode", "UPI").param("taxType", "CGST_SGST").param("gstPercent", "5")
                .param("items[0].particulars", "Mysore Silk Saree").param("items[0].qty", "10").param("items[0].rate", "1300"));
        Bill bill = billRepo.findByCustomerNameIgnoreCaseOrderByBillDateDescIdDesc("Ravi Textiles").get(0);
        assertEquals(0, new BigDecimal("10").compareTo(stockOf("Mysore Silk Saree")));
        assertEquals(0, new BigDecimal("13650.00").compareTo(bill.getGrandTotal()));   // 13000 + 5% GST

        // editing the bill moves stock by the difference only
        mvc.perform(post("/bills/save").session(s).param("id", String.valueOf(bill.getId()))
                .param("customerName", "Ravi Textiles").param("billDate", today).param("billType", "CREDIT")
                .param("taxType", "CGST_SGST").param("gstPercent", "5")
                .param("items[0].particulars", "Mysore Silk Saree").param("items[0].qty", "12").param("items[0].rate", "1300"));
        assertEquals(0, new BigDecimal("8").compareTo(stockOf("Mysore Silk Saree")));
        bill = billRepo.findById(bill.getId()).orElseThrow();   // 15600 + 780 = 16380

        // ---- local bill takes stock out; cancelling puts it back, restoring takes it out again
        mvc.perform(post("/local/save").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billDate\":\"" + today + "\",\"paymentMode\":\"UPI\",\"items\":[{\"name\":\"mysore silk saree\",\"qty\":4,\"mrp\":1800,\"rate\":1500}]}"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"paymentMode\":\"UPI\"")));
        assertEquals(0, new BigDecimal("4").compareTo(stockOf("Mysore Silk Saree")));
        assertTrue(productRepo.findFirstByNameIgnoreCase("Mysore Silk Saree").orElseThrow().isLowStock());
        mvc.perform(get("/").session(s)).andExpect(status().isOk()).andExpect(content().string(containsString("Low-stock alerts")));
        mvc.perform(post("/bills/" + bill.getId() + "/cancel").session(s).param("value", "true"));
        assertEquals(0, new BigDecimal("16").compareTo(stockOf("Mysore Silk Saree")));
        mvc.perform(post("/bills/" + bill.getId() + "/cancel").session(s).param("value", "false"));
        assertEquals(0, new BigDecimal("4").compareTo(stockOf("Mysore Silk Saree")));

        // ---- supplier + purchase adds stock; payments reduce what we owe
        mvc.perform(post("/suppliers/save").session(s).param("name", "Surat Silk Mills").param("phone", "9000000000"));
        Long supId = supplierRepo.findAllByOrderByNameAsc().get(0).getId();
        mvc.perform(post("/purchases/save").session(s).param("supplierId", String.valueOf(supId)).param("invoiceNo", "SSM-77")
                        .param("purchaseDate", today).param("otherCharges", "500")
                        .param("items[0].productName", "Mysore Silk Saree").param("items[0].qty", "30").param("items[0].rate", "1000"))
                .andExpect(status().is3xxRedirection());
        assertEquals(0, new BigDecimal("34").compareTo(stockOf("Mysore Silk Saree")));
        mvc.perform(post("/suppliers/" + supId + "/pay").session(s).param("amount", "10000").param("mode", "BANK").param("date", today));
        mvc.perform(get("/suppliers/" + supId).session(s)).andExpect(content().string(containsString("20,500.00")));   // 30500 - 10000

        // ---- customer payment: part, then over the balance is refused, then the rest marks the bill paid
        mvc.perform(get("/customers/outstanding").session(s)).andExpect(content().string(containsString("Ravi Textiles")))
                .andExpect(content().string(containsString("16,380.00")));
        Long custId = billRepo.findById(bill.getId()).map(b -> b.getId()).orElseThrow();
        Long customerId = mvc.perform(get("/customers").session(s)).andReturn().getResponse().getContentAsString()
                .lines().filter(l -> l.contains("href=\"/customers/") && !l.contains("/edit") && !l.contains("/new")
                        && !l.contains("outstanding")).map(l -> l.replaceAll(".*href=\"/customers/(\\d+)\".*", "$1"))
                .filter(x -> x.matches("\\d+")).map(Long::valueOf).findFirst().orElseThrow();
        mvc.perform(post("/customers/" + customerId + "/receive").session(s).param("amount", "6380").param("mode", "UPI").param("date", today))
                .andExpect(flash().attribute("msg", "Payment of ₹6380 saved"));
        Bill partly = billRepo.findById(custId).orElseThrow();
        assertFalse(partly.isPaid());
        assertEquals(0, new BigDecimal("10000.00").compareTo(partly.getBalance()));
        mvc.perform(post("/customers/" + customerId + "/receive").session(s).param("amount", "20000"))
                .andExpect(flash().attribute("err", containsString("more than the outstanding")));
        mvc.perform(post("/customers/" + customerId + "/receive").session(s).param("amount", "10000").param("mode", "CASH"));
        assertTrue(billRepo.findById(custId).orElseThrow().isPaid());

        // ---- expense and return
        mvc.perform(post("/expenses").session(s).param("date", today).param("category", "Rent").param("amount", "8000").param("mode", "CASH"))
                .andExpect(flash().attribute("msg", "Expense saved"));
        mvc.perform(post("/returns").session(s).param("date", today).param("productName", "Mysore Silk Saree")
                .param("qty", "1").param("refund", "1500").param("mode", "CASH").param("reason", "colour"));
        assertEquals(0, new BigDecimal("35").compareTo(stockOf("Mysore Silk Saree")));

        // ---- profit: sales 15600 (wholesale, no GST) + 6000 (local) - 1500 return = 20100;
        //      cost (12 + 4 - 1) x 1000 = 15000; gross 5100; minus rent 8000 = -2900
        var sum = insights.summary(LocalDate.now(), LocalDate.now());
        assertEquals(0, new BigDecimal("20100").compareTo(sum.netSales()));
        assertEquals(0, new BigDecimal("15000").compareTo(sum.cogs()));
        assertEquals(0, new BigDecimal("-2900").compareTo(sum.netProfit()));
        // money in: local 6000 UPI + receipts 6380 UPI + 10000 cash; out: rent 8000 cash, supplier 10000 bank, refund 1500 cash
        assertEquals(0, new BigDecimal("12380").compareTo(sum.moneyIn().get("UPI")));
        assertEquals(0, new BigDecimal("10000").compareTo(sum.moneyIn().get("CASH")));
        assertEquals(0, new BigDecimal("9500").compareTo(sum.moneyOut().get("CASH")));

        // ---- stock adjustment needs a reason and is logged; price change is logged
        Product p = productRepo.findFirstByNameIgnoreCase("Mysore Silk Saree").orElseThrow();
        mvc.perform(post("/products/" + p.getId() + "/stock").session(s).param("change", "-2").param("reason", ""))
                .andExpect(flash().attribute("err", containsString("reason")));
        mvc.perform(post("/products/" + p.getId() + "/stock").session(s).param("change", "-2").param("reason", "damaged"));
        assertEquals(0, new BigDecimal("33").compareTo(stockOf("Mysore Silk Saree")));
        mvc.perform(post("/products/save").session(s).param("id", String.valueOf(p.getId())).param("name", "Mysore Silk Saree")
                .param("rate", "1600").param("purchasePrice", "1000").param("wholesalePrice", "1300").param("stockQty", "999"));
        assertEquals(0, new BigDecimal("33").compareTo(stockOf("Mysore Silk Saree")), "edit form cannot overwrite stock");
        mvc.perform(get("/activity").session(s)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Stock adjusted")))
                .andExpect(content().string(containsString("Selling price 1500 → 1600")))
                .andExpect(content().string(containsString("Bill cancelled")));

        // ---- every page renders
        for (String url : new String[]{"/", "/products", "/products/new", "/products/" + p.getId() + "/edit", "/suppliers",
                "/suppliers/new", "/suppliers/" + supId, "/purchases", "/purchases/new", "/customers", "/customers/" + customerId,
                "/customers/outstanding", "/expenses", "/returns", "/accounts", "/activity", "/bills/new", "/local/new"}) {
            mvc.perform(get(url).session(s)).andExpect(status().isOk());
        }
        for (String tab : new String[]{"sales", "summary", "items", "purchases", "stock", "profit", "outstanding"}) {
            mvc.perform(get("/reports").param("tab", tab).param("group", "MONTH").session(s)).andExpect(status().isOk());
        }
        for (String type : new String[]{"summary", "items", "purchases", "stock", "profit", "outstanding"}) {
            mvc.perform(get("/reports/export").param("type", type).session(s)).andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString(type + "_")));
        }
        mvc.perform(get("/reports").param("tab", "items").session(s)).andExpect(content().string(containsString("Mysore Silk Saree")));
        mvc.perform(get("/reports/export").param("type", "../etc").session(s)).andExpect(status().isNotFound());
    }
}
