package com.example.shop.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Typed settings under "app.shop.*"; the application refuses to start if they are invalid
@Validated
@ConfigurationProperties(prefix = "app.shop")
public class ShopProperties {

    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be an ISO-4217 code, e.g. KZT")
    private String currency = "KZT";

    // Upper limit for the number of lines in one order
    @Min(1)
    @Max(100)
    private int maxItemsPerOrder = 20;

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getMaxItemsPerOrder() {
        return maxItemsPerOrder;
    }

    public void setMaxItemsPerOrder(int maxItemsPerOrder) {
        this.maxItemsPerOrder = maxItemsPerOrder;
    }
}
