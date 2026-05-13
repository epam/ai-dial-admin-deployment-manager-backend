package com.epam.aidial.deployment.manager.web.security.apikey;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyPropertiesTest {

    @Test
    void shouldNoOpWhenDisabled() {
        ApiKeyProperties props = new ApiKeyProperties(new ObjectMapper());
        props.setEnabled(false);
        // No URL configured — should not throw because the feature is disabled.
        assertThatCode(props::validate).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMissingCoreUrlWhenEnabled() {
        ApiKeyProperties props = new ApiKeyProperties(new ObjectMapper());
        props.setEnabled(true);
        props.setCoreUrl("");
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("core-url");
    }

    @Test
    void shouldRejectInvalidRolesMappingJson() {
        ApiKeyProperties props = new ApiKeyProperties(new ObjectMapper());
        props.setEnabled(true);
        props.setCoreUrl("http://core");
        props.setRolesMapping("not-json");
        props.setStartupProbe(false);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roles-mapping");
    }

    @Test
    void shouldRejectBlankRolesMappingWhenEnabled() {
        ApiKeyProperties props = new ApiKeyProperties(new ObjectMapper());
        props.setEnabled(true);
        props.setCoreUrl("http://core");
        props.setRolesMapping("");
        props.setStartupProbe(false);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roles-mapping");
    }

    @Test
    void shouldRejectEmptyRolesMappingWhenEnabled() {
        ApiKeyProperties props = new ApiKeyProperties(new ObjectMapper());
        props.setEnabled(true);
        props.setCoreUrl("http://core");
        props.setRolesMapping("{}");
        props.setStartupProbe(false);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roles-mapping");
    }

    @Test
    void shouldParseRolesMapping() {
        ApiKeyProperties props = new ApiKeyProperties(new ObjectMapper());
        props.setEnabled(true);
        props.setCoreUrl("http://core");
        props.setRolesMapping("{\"admin\":[\"FULL_ADMIN\"]}");
        props.setStartupProbe(false);
        props.validate();
        assertThat(props.getParsedRolesMapping()).containsKey("admin");
    }
}
