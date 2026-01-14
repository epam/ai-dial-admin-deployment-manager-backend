package com.epam.aidial.deployment.manager.configuration.kubernetes;

import io.fabric8.kubernetes.api.model.IntOrString;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;

public class IntegerToIntOrStringConverter implements Converter<Integer, IntOrString> {

    @NotNull
    @Override
    public IntOrString convert(@NotNull Integer source) {
        return new IntOrString(source);
    }

}