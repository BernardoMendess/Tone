package com.tone.domain;

import com.tone.domain.entity.*;
import com.tone.domain.enums.PaymentMethod;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SplitCalculationTest {

    @ParameterizedTest
    @CsvSource({
            "0.01, 15.00",
            "1.00, 15.00",
            "1.00, 33.33",
            "49.90, 15.00",
            "59.99, 17.50",
            "108.90, 22.75",
            "999.99, 40.00",
            "10.01, 1.00"
    })
    void feePlusNetAlwaysEqualsGrossWithoutRoundingDrift(String priceStr, String takeRateStr) {
        User buyer = new User();
        buyer.setId(UUID.randomUUID());

        CreatorProfile creator = new CreatorProfile();
        creator.setId(UUID.randomUUID());
        creator.setPixKey("creator@tone.com");

        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setCreator(creator);
        product.setTitle("Test Tone");
        product.setPrice(new BigDecimal(priceStr));
        product.setCurrentVersion("1.0.0");

        CartItem ci = new CartItem();
        ci.setUser(buyer);
        ci.setProduct(product);

        Order order = Order.place(
                buyer,
                "idem-" + UUID.randomUUID(),
                PaymentMethod.PIX,
                List.of(ci),
                new BigDecimal(takeRateStr),
                Instant.now()
        );

        assertThat(order.getPlatformFeeTotal().add(order.getCreatorNetTotal()))
                .isEqualByComparingTo(order.getTotalAmount());
        assertThat(order.getSplits()).hasSize(1);
        PaymentSplit split = order.getSplits().getFirst();
        assertThat(split.getPlatformFeeAmount().add(split.getCreatorNetAmount()))
                .isEqualByComparingTo(split.getGrossAmount());
        assertThat(order.getOrderCode()).matches("^TN-\\d{4}-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{10}$");
    }
}
