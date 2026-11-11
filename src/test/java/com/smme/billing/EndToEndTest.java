package com.smme.billing;

import com.smme.billing.model.Bill;
import com.smme.billing.repository.BillRepository;
import com.smme.billing.repository.CustomerRepository;
import com.smme.billing.repository.LocalBillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Base64;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Walks through the whole application the way a shop user would. */
@SpringBootTest
@AutoConfigureMockMvc
class EndToEndTest {

    // 1x1 transparent PNG
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired MockMvc mvc;
    @Autowired BillRepository billRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired LocalBillRepository localBillRepo;

    private MockHttpSession login(String user, String pass) throws Exception {
        MvcResult r = mvc.perform(post("/login").param("username", user).param("password", pass))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    @Test
    void fullShopWorkflow() throws Exception {
        // ---- not logged in: everything redirects to login, static files are public
        mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/bills")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("admin123"))));
        mvc.perform(get("/img/logo.svg")).andExpect(status().isOk());
        mvc.perform(get("/css/style.css")).andExpect(status().isOk());

        // ---- wrong password
        mvc.perform(post("/login").param("username", "admin").param("password", "wrong"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invalid username or password")));

        // ---- default password forces a password change first
        MockHttpSession s = login("admin", "admin123");
        mvc.perform(get("/").session(s)).andExpect(redirectedUrl("/settings#password"));
        mvc.perform(get("/settings").session(s)).andExpect(status().isOk())
                .andExpect(content().string(containsString("default password")));
        mvc.perform(post("/settings/password").session(s)
                        .param("oldPassword", "admin123").param("newPassword", "abc").param("confirmPassword", "abc"))
                .andExpect(flash().attribute("err", containsString("at least 6")));
        mvc.perform(post("/settings/password").session(s)
                        .param("oldPassword", "admin123").param("newPassword", "secret99").param("confirmPassword", "nomatch"))
                .andExpect(flash().attribute("err", containsString("do not match")));
        mvc.perform(post("/settings/password").session(s)
                        .param("oldPassword", "admin123").param("newPassword", "secret99").param("confirmPassword", "secret99"))
                .andExpect(flash().attribute("msg", "Password changed"));
        mvc.perform(get("/").session(s)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Dashboard")));

        // old password no longer works, new one does
        mvc.perform(post("/login").param("username", "admin").param("password", "admin123"))
                .andExpect(status().isOk());
        s = login("admin", "secret99");
        mvc.perform(get("/").session(s)).andExpect(status().isOk());

        // ---- shop settings
        mvc.perform(post("/settings").session(s)
                        .param("shopName", "Test Traders").param("address", "1 Main Road, Bengaluru")
                        .param("gstin", "29abcde1234f1z5").param("mobile", "9999999999")
                        .param("stateName", "Karnataka").param("stateCode", "29")
                        .param("nextBillNo", "500").param("defaultGst", "18")
                        .param("bankDetails", "SBI A/c 123").param("terms", "No returns")
                        .param("showLogo", "true").param("_showLogo", "on"))
                .andExpect(flash().attribute("msg", "Settings saved"));
        mvc.perform(post("/settings").session(s).param("shopName", " ").param("nextBillNo", "1"))
                .andExpect(flash().attribute("err", "Shop name is required"));

        // ---- logo upload: rejects non-images, accepts a PNG, serves it
        mvc.perform(multipart("/settings/logo").file(new MockMultipartFile("logo", "x.png", "image/png", "<svg/>".getBytes())).session(s))
                .andExpect(flash().attribute("err", containsString("PNG, JPG")));
        mvc.perform(multipart("/settings/logo").file(new MockMultipartFile("logo", "logo.png", "image/png", PNG)).session(s))
                .andExpect(flash().attribute("msg", "Logo uploaded"));
        mvc.perform(get("/shop-logo")).andExpect(status().isOk()).andExpect(content().contentType("image/png"));

        // ---- items and customers
        mvc.perform(post("/products/save").session(s).param("name", "Cement Bag").param("hsn", "2523")
                .param("unit", "Bag").param("rate", "410")).andExpect(flash().attribute("msg", "Item saved"));
        mvc.perform(post("/products/save").session(s).param("name", "  ")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Item name is required")));
        mvc.perform(get("/products").session(s)).andExpect(content().string(containsString("Cement Bag")));
        mvc.perform(post("/customers/save").session(s).param("name", "Ravi Constructions").param("gstin", "BAD"))
                .andExpect(content().string(containsString("GSTIN must be exactly 15")));
        mvc.perform(post("/customers/save").session(s).param("name", "Ravi Constructions").param("phone", "88888"))
                .andExpect(flash().attribute("msg", "Customer saved"));
        mvc.perform(get("/customers").session(s)).andExpect(content().string(containsString("Ravi Constructions")));

        // ---- new bill form
        mvc.perform(get("/bills/new").session(s)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Bill No will be: 500")));

        // invalid bill is shown again with an error, nothing saved
        mvc.perform(post("/bills/save").session(s)
                        .param("customerName", "Ravi Constructions").param("billDate", "2026-11-01")
                        .param("billType", "CASH").param("taxType", "CGST_SGST").param("gstPercent", "18")
                        .param("discount", "9999")
                        .param("items[0].particulars", "Cement Bag").param("items[0].qty", "2").param("items[0].rate", "410"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Discount cannot be more than the sub total")));
        assertEquals(0, billRepo.count());

        // valid credit bill
        MvcResult saved = mvc.perform(post("/bills/save").session(s)
                        .param("customerName", "New Buyer").param("customerGstin", "29aaaaa0000a1z5")
                        .param("billDate", "2026-11-01").param("billType", "CREDIT")
                        .param("taxType", "CGST_SGST").param("gstPercent", "18").param("discount", "20")
                        .param("items[0].particulars", "Cement Bag").param("items[0].hsn", "2523")
                        .param("items[0].qty", "10").param("items[0].unit", "Bag").param("items[0].rate", "410")
                        .param("items[1].particulars", "").param("items[1].qty", "")
                        .param("items[2].particulars", "Transport").param("items[2].qty", "1").param("items[2].rate", "350"))
                .andExpect(status().is3xxRedirection()).andReturn();
        Bill bill = billRepo.findByBillNo(500L).orElseThrow();
        assertEquals("/bills/" + bill.getId(), saved.getResponse().getRedirectedUrl());
        // (10 x 410 + 350 - 20) = 4430 taxable, + 9% CGST 398.70 + 9% SGST 398.70 = 5227.40, rounded to 5227
        assertEquals(0, new java.math.BigDecimal("5227.00").compareTo(bill.getGrandTotal()));
        assertEquals(0, new java.math.BigDecimal("398.70").compareTo(bill.getCgst()));
        assertFalse(bill.isPaid());
        assertTrue(customerRepo.findFirstByNameIgnoreCase("New Buyer").isPresent(), "new customer auto-saved");
        long id = bill.getId();

        // view, print A4/A5/duplicate
        mvc.perform(get("/bills/" + id).session(s)).andExpect(status().isOk())
                .andExpect(content().string(containsString("New Buyer")))
                .andExpect(content().string(containsString("Transport")))
                .andExpect(content().string(containsString("5,227.00")));
        mvc.perform(get("/bills/" + id + "/print").session(s)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Tax Invoice No")))
                .andExpect(content().string(containsString("Signature of consignee")))
                .andExpect(content().string(containsString("Test Traders")))
                .andExpect(content().string(containsString("/shop-logo")))
                .andExpect(content().string(not(containsString("DUPLICATE COPY"))));
        mvc.perform(get("/bills/" + id + "/print").param("size", "A5").param("copy", "DUPLICATE").session(s))
                .andExpect(status().isOk()).andExpect(content().string(containsString("DUPLICATE COPY")));
        // injected size value must never reach the page
        mvc.perform(get("/bills/" + id + "/print").param("size", "A4;}</style><script>alert(1)</script>").session(s))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("<script>alert(1)"))));

        // listing, search, filters, dashboard
        mvc.perform(get("/bills").session(s)).andExpect(content().string(containsString("New Buyer")));
        mvc.perform(get("/bills").param("q", "500").session(s)).andExpect(content().string(containsString("New Buyer")));
        mvc.perform(get("/bills").param("status", "UNPAID").session(s)).andExpect(content().string(containsString("New Buyer")));
        mvc.perform(get("/").session(s)).andExpect(status().isOk());

        // edit bill
        mvc.perform(get("/bills/" + id + "/edit").session(s)).andExpect(status().isOk());
        mvc.perform(post("/bills/save").session(s).param("id", String.valueOf(id))
                        .param("customerName", "New Buyer").param("billDate", "2026-11-02").param("billType", "CREDIT")
                        .param("taxType", "IGST").param("gstPercent", "18").param("discount", "0")
                        .param("items[0].particulars", "Cement Bag").param("items[0].qty", "1").param("items[0].rate", "100"))
                .andExpect(redirectedUrl("/bills/" + id));
        assertEquals(0, new java.math.BigDecimal("118.00").compareTo(billRepo.findById(id).orElseThrow().getGrandTotal()));
        assertEquals(500L, billRepo.findById(id).orElseThrow().getBillNo());

        // paid / copy / cancel / restore
        mvc.perform(post("/bills/" + id + "/paid").session(s).param("value", "true"))
                .andExpect(flash().attribute("msg", "Bill marked as paid"));
        assertTrue(billRepo.findById(id).orElseThrow().isPaid());
        mvc.perform(get("/bills/" + id + "/copy").session(s)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Bill No will be: 501")));
        mvc.perform(post("/bills/" + id + "/cancel").session(s).param("value", "true"))
                .andExpect(flash().attribute("msg", "Bill cancelled"));
        mvc.perform(get("/bills/" + id + "/edit").session(s)).andExpect(redirectedUrl("/bills/" + id));
        mvc.perform(get("/bills/" + id + "/print").session(s)).andExpect(content().string(containsString("CANCELLED")));
        mvc.perform(post("/bills/" + id + "/cancel").session(s).param("value", "false"))
                .andExpect(flash().attribute("msg", "Bill restored"));

        // reports and CSV
        mvc.perform(get("/reports").param("from", "2026-11-01").param("to", "2026-11-30").session(s))
                .andExpect(status().isOk()).andExpect(content().string(containsString("New Buyer")));
        mvc.perform(get("/reports/csv").param("from", "2026-11-01").param("to", "2026-11-30").session(s))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("sales_2026-11-01_to_2026-11-30.csv")))
                .andExpect(content().string(containsString("\"New Buyer\"")));

        // ---- errors: missing record -> 404 page, bad id -> 400, cross-site POST blocked
        mvc.perform(get("/bills/999999").session(s)).andExpect(status().isNotFound());
        mvc.perform(get("/customers/999999/edit").session(s)).andExpect(status().isNotFound());
        mvc.perform(get("/bills/abc").session(s)).andExpect(status().isBadRequest());
        mvc.perform(post("/bills/" + id + "/cancel").session(s).param("value", "true")
                .header("Origin", "http://evil.example.com")).andExpect(status().isForbidden());
        assertFalse(billRepo.findById(id).orElseThrow().isCancelled());

        // ---- local (retail) billing: receipt with MRP / rate / savings, numbered 0001/<year>
        int year = LocalDate.now().getYear();
        String today = LocalDate.now().toString();
        mvc.perform(get("/local/new").session(s)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Local Bill Entry")))
                .andExpect(content().string(containsString("Cement Bag")));
        mvc.perform(get("/local/api/next").session(s))
                .andExpect(jsonPath("$.billNo").value(String.format("0001/%d", year)));
        String localJson = "{\"billDate\":\"" + today + "\",\"customerName\":\"Walk In\",\"custMobile\":\"9000000001\","
                + "\"items\":[{\"name\":\"Saree\",\"qty\":2,\"mrp\":500,\"rate\":450},"
                + "{\"name\":\"\",\"qty\":1,\"mrp\":0,\"rate\":0}]}";
        MvcResult lr = mvc.perform(post("/local/save").session(s).contentType("application/json").content(localJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billNo").value(String.format("0001/%d", year)))
                .andExpect(jsonPath("$.totalSale").value(900.0))
                .andExpect(jsonPath("$.totalSavings").value(100.0))
                .andExpect(jsonPath("$.netPayable").value(900.0))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();
        long localId = localBillRepo.findByBillNo(String.format("0001/%d", year)).orElseThrow().getId();
        assertTrue(lr.getResponse().getContentAsString().contains("Walk In"));
        // saving again with the id updates the same bill, no new number
        mvc.perform(post("/local/save").session(s).contentType("application/json")
                        .content(localJson.replace("{\"billDate\"", "{\"id\":" + localId + ",\"billDate\"")))
                .andExpect(jsonPath("$.billNo").value(String.format("0001/%d", year)));
        assertEquals(1, localBillRepo.count());
        mvc.perform(post("/local/save").session(s).contentType("application/json")
                        .content("{\"items\":[{\"name\":\"Shirt\",\"qty\":0,\"mrp\":10,\"rate\":5}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(containsString("Quantity")));
        mvc.perform(get("/local/api/next").session(s))
                .andExpect(jsonPath("$.billNo").value(String.format("0002/%d", year)));
        mvc.perform(get("/local/" + localId).session(s)).andExpect(status().isOk());
        mvc.perform(get("/local/api/report").param("date", today).session(s))
                .andExpect(jsonPath("$[0].customerName").value("Walk In"));
        mvc.perform(get("/local").param("q", "walk").session(s)).andExpect(content().string(containsString("9000000001")));
        mvc.perform(get("/").session(s)).andExpect(content().string(containsString("Today – Local")))
                .andExpect(content().string(containsString("Walk In")));
        mvc.perform(get("/reports").param("from", today).param("to", today).session(s))
                .andExpect(content().string(containsString("Walk In")));
        mvc.perform(get("/reports/local-csv").param("from", today).param("to", today).session(s))
                .andExpect(content().string(containsString("\"Walk In\"")));
        mvc.perform(post("/local/" + localId + "/cancel").session(s).param("value", "true"))
                .andExpect(flash().attribute("msg", "Local bill cancelled"));
        mvc.perform(post("/local/save").session(s).contentType("application/json")
                        .content(localJson.replace("{\"billDate\"", "{\"id\":" + localId + ",\"billDate\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/local/api/report").param("date", today).session(s)).andExpect(content().json("[]"));
        mvc.perform(post("/local/" + localId + "/cancel").session(s).param("value", "false"))
                .andExpect(flash().attribute("msg", "Local bill restored"));
        mvc.perform(get("/local/999999").session(s)).andExpect(status().isNotFound());

        // ---- remove logo, delete customer/item, logout
        mvc.perform(post("/settings/logo/delete").session(s)).andExpect(flash().attribute("msg", "Logo removed"));
        mvc.perform(get("/shop-logo")).andExpect(status().isNotFound());
        mvc.perform(get("/bills/" + id + "/print").session(s)).andExpect(content().string(not(containsString("src=\"/shop-logo\""))))
                .andExpect(content().string(containsString("/img/brand-lakshmi.jpg")));   // default Lakshmi image when no logo uploaded

        long custId = customerRepo.findFirstByNameIgnoreCase("Ravi Constructions").orElseThrow().getId();
        mvc.perform(post("/customers/" + custId + "/delete").session(s)).andExpect(flash().attribute("msg", "Customer deleted"));

        mvc.perform(post("/logout").session(s)).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/").session(s)).andExpect(redirectedUrl("/login"));
    }
}
