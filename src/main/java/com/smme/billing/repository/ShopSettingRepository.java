package com.smme.billing.repository;

import com.smme.billing.model.ShopSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopSettingRepository extends JpaRepository<ShopSetting, Long> {
}
