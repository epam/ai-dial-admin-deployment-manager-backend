package com.epam.aidial.deployment.manager.utils;

import io.fabric8.kubernetes.api.model.Quantity;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

@UtilityClass
public class KubernetesQuantityParser {

    /**
     * Parses a Kubernetes resource quantity string and returns the value in bytes.
     * Accepts standard Kubernetes quantity formats: plain bytes ("21474836480"),
     * binary suffixes ("20Gi", "500Mi"), and decimal suffixes ("20G", "500M").
     *
     * @return the value in bytes, or null if the input is invalid
     */
    public static BigDecimal parseToBytes(String value) {
        try {
            return Quantity.getAmountInBytes(Quantity.parse(value));
        } catch (Exception e) {
            return null;
        }
    }
}
