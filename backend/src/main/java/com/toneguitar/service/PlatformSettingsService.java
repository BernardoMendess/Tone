package com.toneguitar.service;

import com.toneguitar.domain.entity.PlatformSetting;
import com.toneguitar.repository.PlatformSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
public class PlatformSettingsService {

    public static final String KEY_TAKE_RATE = "DEFAULT_TAKE_RATE_PERCENT";

    private final PlatformSettingRepository settingRepository;
    private final BigDecimal fallbackTakeRate;

    public PlatformSettingsService(
            PlatformSettingRepository settingRepository,
            @Value("${app.payment.default-take-rate-percent:15.00}") BigDecimal fallbackTakeRate
    ) {
        this.settingRepository = settingRepository;
        this.fallbackTakeRate = fallbackTakeRate;
    }

    public BigDecimal getTakeRatePercent() {
        return settingRepository.findById(KEY_TAKE_RATE)
                .map(s -> new BigDecimal(s.getSettingValue()).setScale(2, RoundingMode.HALF_UP))
                .orElse(fallbackTakeRate.setScale(2, RoundingMode.HALF_UP));
    }

    @Transactional
    public BigDecimal updateTakeRatePercent(BigDecimal newRate) {
        BigDecimal scaled = newRate.setScale(2, RoundingMode.HALF_UP);
        PlatformSetting setting = settingRepository.findById(KEY_TAKE_RATE)
                .orElseGet(() -> new PlatformSetting(KEY_TAKE_RATE, scaled.toPlainString()));
        setting.setSettingValue(scaled.toPlainString());
        setting.setUpdatedAt(Instant.now());
        settingRepository.save(setting);
        return scaled;
    }
}
