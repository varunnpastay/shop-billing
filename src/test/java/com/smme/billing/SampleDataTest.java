package com.smme.billing;

import com.smme.billing.controller.AuthController;
import com.smme.billing.model.*;
import com.smme.billing.repository.*;
import com.smme.billing.service.InsightService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Loads the sample data, then opens every page of the app (lists, details, edit forms, prints, reports, exports). */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:sample;DB_CLOSE_DELAY=-1", "sample-data=true"})
@AutoConfigureMockMvc
class SampleDataTest {

    @Autowired MockMvc mvc;
    @Autowired ProductRepository productRepo;
    @Autowired BillRepository billRepo;
    @Autowired LocalBillRepository localRepo;
    @Autowired SupplierRepository supplierRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired PurchaseRepository purchaseRepo;
    @Autowired InsightService insights;

    private BigDecimal stockOf(String name) {
        return productRepo.findFirstByNameIgnoreCase(name).orElseThrow().stock();
    }

    @Test
    void sampleDataFillsEveryScreen() throws Exception {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(AuthController.SESSION_USER, "admin");

        // the data is consistent: stock follows opening + purchases - sales + returns
        assertEquals(10, productRepo.count());
        assertEquals(3, supplierRepo.count());
        assertEquals(0, new BigDecimal("29").compareTo(stockOf("Mysore Silk Saree")));     // 24 + 20 - 8 - 1 - 5 - 1
        assertEquals(0, new BigDecimal("22").compareTo(stockOf("Ilkal Saree")));           // 18 + 15 - 6 - 1 - 4; the cancelled bill put its 1 back
        assertEquals(0, new BigDecimal("17").compareTo(stockOf("Kanchipuram Pattu Saree"))); // 12 + 8 - 3 - 1 + 1 returned
        assertFalse(insights.stock().low().isEmpty(), "some items are low on stock");
        assertTrue(insights.receivables().signum() > 0, "customers owe money");
        assertTrue(insights.payables().signum() > 0, "we owe suppliers");

        // dashboard: today's total with the split inside it, pie charts, and the old tiles gone
        mvc.perform(get("/").session(s)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Today – Total")))
                .andExpect(content().string(containsString("today-split")))
                .andExpect(content().string(containsString("Sales by category")))
                .andExpect(content().string(containsString("pie-slice")))
                .andExpect(content().string(not(containsString("GST This Month"))))
                .andExpect(content().string(not(containsString("Stock Value"))));

        // adding a supplier from the form works
        mvc.perform(post("/suppliers/save").session(s).param("id", "").param("name", "New Supplier")
                        .param("phone", "9000000000").param("gstin", ""))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("msg", "Supplier saved"));
        mvc.perform(post("/suppliers/save").session(s).param("name", "Bad GSTIN").param("gstin", "123"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("GSTIN must be exactly 15 characters")));

        // every page opens
        List<String> urls = new ArrayList<>(List.of("/", "/local", "/local/new", "/bills", "/bills/new", "/returns",
                "/products", "/products/new", "/products?low=true", "/purchases", "/purchases/new", "/suppliers",
                "/suppliers/new", "/customers", "/customers/new", "/customers/outstanding", "/expenses", "/accounts",
                "/activity", "/reports", "/settings", "/bills?status=UNPAID", "/local/api/saved", "/local/api/next"));
        for (Bill b : billRepo.findAll()) {
            urls.add("/bills/" + b.getId());
            urls.add("/bills/" + b.getId() + "/print");
            urls.add("/bills/" + b.getId() + "/print?size=A5");
            urls.add("/bills/" + b.getId() + "/edit");
            urls.add("/bills/" + b.getId() + "/copy");
        }
        for (LocalBill b : localRepo.findAll()) {
            urls.add("/local/" + b.getId());
            urls.add("/local/api/bills/" + b.getId());
        }
        for (Product p : productRepo.findAll()) urls.add("/products/" + p.getId() + "/edit");
        for (Supplier p : supplierRepo.findAll()) {
            urls.add("/suppliers/" + p.getId());
            urls.add("/suppliers/" + p.getId() + "/edit");
        }
        for (Customer c : customerRepo.findAll()) {
            urls.add("/customers/" + c.getId());
            urls.add("/customers/" + c.getId() + "/edit");
        }
        for (Purchase p : purchaseRepo.findAll()) {
            urls.add("/purchases/" + p.getId());
            urls.add("/purchases/" + p.getId() + "/edit");
        }
        String from = LocalDate.now().minusMonths(2).toString(), to = LocalDate.now().toString();
        for (String tab : new String[]{"sales", "summary", "items", "purchases", "stock", "profit", "outstanding"})
            for (String group : new String[]{"DAY", "MONTH", "YEAR"})
                urls.add("/reports?tab=" + tab + "&group=" + group + "&from=" + from + "&to=" + to);
        for (String type : new String[]{"summary", "items", "purchases", "stock", "profit", "outstanding"})
            urls.add("/reports/export?type=" + type + "&from=" + from + "&to=" + to);
        urls.add("/reports/csv?from=" + from + "&to=" + to);
        urls.add("/reports/local-csv?from=" + from + "&to=" + to);
        urls.add("/local/api/report?date=" + to);

        List<String> failed = new ArrayList<>();
        for (String url : urls) {
            int code = mvc.perform(get(url).session(s)).andReturn().getResponse().getStatus();
            if (code != 200) failed.add(code + " " + url);
        }
        assertTrue(failed.isEmpty(), "pages that did not open: " + failed);

        // pages show the sample data
        mvc.perform(get("/expenses").session(s)).andExpect(content().string(containsString("HESCOM bill")));
        mvc.perform(get("/returns").session(s)).andExpect(content().string(containsString("Colour not liked")));
        mvc.perform(get("/activity").session(s)).andExpect(content().string(containsString("Sample data added")));
        mvc.perform(get("/customers/outstanding").session(s)).andExpect(content().string(containsString("Sri Ganesh Cloth Stores")));
    }
}
