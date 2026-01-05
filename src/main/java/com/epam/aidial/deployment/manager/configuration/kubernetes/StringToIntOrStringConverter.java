package com.epam.aidial.deployment.manager.configuration.kubernetes;

import io.fabric8.kubernetes.api.model.IntOrString;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;

public class StringToIntOrStringConverter implements Converter<String, IntOrString> {

    @NotNull
    @Override
    public IntOrString convert(@NotNull String source) {
        return new IntOrString(source);
    }

}