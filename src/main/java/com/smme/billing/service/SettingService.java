package com.smme.billing.service;

import com.smme.billing.model.ShopSetting;
import com.smme.billing.repository.ShopSettingRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class SettingService {

    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin123";
    public static final int MIN_PASSWORD_LENGTH = 6;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final ShopSettingRepository repo;

    public SettingService(ShopSettingRepository repo) {
        this.repo = repo;
    }

    public ShopSetting get() {
        ShopSetting s = repo.findById(1L).orElseGet(() -> repo.save(defaults()));
        if (s.getReceiptConfigured() == null) {   // database from before local billing was added
            applyReceiptDefaults(s);
            s = repo.save(s);
        }
        if (PLACEHOLDER_SHOP_NAME.equals(s.getShopName())) {   // shop details never filled in yet
            applyShopDefaults(s);
            s = repo.save(s);
        }
        if (s.getInvoiceConfigured() == null) {   // database from before the printed-bill-book format
            applyInvoiceDefaults(s);
            if (OLD_DEFAULT_TERMS.equals(s.getTerms())) s.setTerms(DEFAULT_TERMS);
            if (s.getDefaultGst() != null && s.getDefaultGst().compareTo(new BigDecimal("18")) == 0)
                s.setDefaultGst(new BigDecimal("5"));   // cloth: 2.5% CGST + 2.5% SGST
            s = repo.save(s);
        }
        return s;
    }

    public ShopSetting save(ShopSetting form) {
        ShopSetting s = get();
        s.setShopName(trim(form.getShopName()));
        s.setAddress(trim(form.getAddress()));
        s.setGstin(upper(form.getGstin()));
        s.setMobile(trim(form.getMobile()));
        s.setEmail(trim(form.getEmail()));
        s.setStateName(trim(form.getStateName()));
        s.setStateCode(trim(form.getStateCode()));
        s.setNextBillNo(form.getNextBillNo());
        s.setDefaultGst(form.getDefaultGst());
        s.setBankDetails(trim(form.getBankDetails()));
        s.setTerms(trim(form.getTerms()));
        s.setShowLogo(form.getShowLogo());
        s.setReceiptTitle(trim(form.getReceiptTitle()));
        s.setReceiptSubtitle(trim(form.getReceiptSubtitle()));
        s.setReceiptDeclaration(trim(form.getReceiptDeclaration()));
        s.setReceiptFooter(trim(form.getReceiptFooter()));
        s.setUpiId(trim(form.getUpiId()));
        s.setInstagram(trim(form.getInstagram()));
        s.setShopNameLocal(trim(form.getShopNameLocal()));
        s.setInvoiceBlessing(trim(form.getInvoiceBlessing()));
        s.setInvoiceAddress(trim(form.getInvoiceAddress()));
        s.setMobile2(trim(form.getMobile2()));
        s.setJurisdiction(trim(form.getJurisdiction()));
        if (form.getNextLocalBillNo() != null) {
            s.setNextLocalBillNo(form.getNextLocalBillNo());
            s.setLocalBillYear(java.time.LocalDate.now().getYear());
        }
        return repo.save(s);
    }

    public void saveRaw(ShopSetting s) {
        repo.save(s);
    }

    public boolean checkLogin(String username, String password) {
        if (username == null || password == null) return false;
        ShopSetting s = get();
        boolean passwordOk = passwordMatches(s, password);   // always evaluated, so timing does not reveal the username
        return passwordOk && username.trim().equals(s.getUsername());
    }

    public boolean changePassword(String oldPassword, String newPassword) {
        ShopSetting s = get();
        if (oldPassword == null || !passwordMatches(s, oldPassword)) return false;
        s.setPasswordHash(ENCODER.encode(newPassword));
        repo.save(s);
        return true;
    }

    /** Checks a password and transparently upgrades legacy SHA-256 hashes to BCrypt. */
    private boolean passwordMatches(ShopSetting s, String password) {
        String stored = s.getPasswordHash();
        if (stored == null || stored.isEmpty()) return false;
        if (stored.startsWith("$2")) return ENCODER.matches(password, stored);

        byte[] expected = stored.getBytes(StandardCharsets.UTF_8);
        byte[] actual = legacySha256(password).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) return false;
        s.setPasswordHash(ENCODER.encode(password));
        repo.save(s);
        return true;
    }

    private static String legacySha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private ShopSetting defaults() {
        ShopSetting s = new ShopSetting();
        s.setId(1L);
        applyShopDefaults(s);
        s.setStateName("Karnataka");
        s.setStateCode("29");
        s.setNextBillNo(1L);
        s.setDefaultGst(new BigDecimal("5"));
        s.setTerms(DEFAULT_TERMS);
        s.setShowLogo(Boolean.TRUE);
        applyReceiptDefaults(s);
        applyInvoiceDefaults(s);
        s.setUsername(DEFAULT_USERNAME);
        s.setPasswordHash(ENCODER.encode(DEFAULT_PASSWORD));
        return s;
    }

    public static final String PLACEHOLDER_SHOP_NAME = "My Shop";
    private static final String OLD_DEFAULT_TERMS = "Goods once sold will not be taken back.\nSubject to local jurisdiction.";
    /** Terms printed at the bottom of the shop's paper bill book. */
    public static final String DEFAULT_TERMS = "Goods once sold cannot be repossessed for any reason. If payment is not made "
            + "in full within 30 days from the purchase limit, an interest of 1-50 per cent of the purchase limit will have "
            + "to be paid. We are not responsible for any damage during transportation.";

    /** Header of the wholesale tax invoice, as on the shop's paper bill book. All of it can be changed in Settings. */
    private static void applyInvoiceDefaults(ShopSetting s) {
        s.setShopNameLocal("ಶ್ರೀ ವಿಜಯಲಕ್ಷ್ಮೀ ಟೆಕ್ಸ್‌ಟೈಲ್ಸ್");
        s.setInvoiceBlessing("|| SHRI GAJANANA PRASANNA ||");
        s.setInvoiceAddress("Whole Sale Handloom & Cloth Merchants, Doddapete Subhas Chowk\n"
                + "Dist. Haveri, RANEBENNUR-581 115. Karnataka State");
        s.setMobile2("7019398498");
        s.setJurisdiction("Subject to Ranebennur Jurisdiction");
        s.setInvoiceConfigured(Boolean.TRUE);
    }

    /** Shop details printed at the top of every bill; all of it can be changed in Settings. */
    private static void applyShopDefaults(ShopSetting s) {
        s.setShopName("Shri Vijaylaxmi Textiles");
        s.setAddress("Doddapete Subhash Chowk, Ranibennur\nDist Haveri");
        s.setMobile("9449187851");
        s.setGstin("29BXLPP4360B1ZC");
    }

    /** Same text the old Smart Billing receipt printed; all of it can be changed in Settings. */
    private static void applyReceiptDefaults(ShopSetting s) {
        s.setReceiptTitle("BILL OF SUPPLY");
        s.setReceiptSubtitle("WHOLESALE CLOTH MERCHANTS");
        s.setReceiptDeclaration("GOODS SOLD ONCE NO RETURN. NO EXCHANGE");
        s.setReceiptFooter("THANK YOU. VISIT US AGAIN.");
        s.setUpiId("9538723988-2@ybl");
        s.setInstagram("sri_vijayalaxmi_textiles_rnr_");
        s.setReceiptConfigured(Boolean.TRUE);
    }

    private static String trim(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String upper(String v) {
        String t = trim(v);
        return t == null ? null : t.toUpperCase();
    }
}
