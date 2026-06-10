package com.epam.aidial.deployment.manager.web.security.apikey;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyPropertiesTest {

    @Test
    void shouldNoOpWhenDisabled() {
        ApiKeyProperties props = newProperties("");
        props.setEnabled(false);
        // No URL configured — should not throw because the feature is disabled.
        assertThatCode(props::validate).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMissingCoreUrlWhenEnabled() {
        ApiKeyProperties props = newProperties("");
        props.setEnabled(true);
        props.setCoreUrl("");
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("core-url");
    }

    @Test
    void shouldRejectInvalidRolesMappingJson() {
        ApiKeyProperties props = newProperties("");
        props.setEnabled(true);
        props.setCoreUrl("http://core");
        props.setRolesMapping("not-json");
        props.setStartupProbe(false);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-key.roles-mapping");
    }

    @Test
    void shouldRejectWhenBothApiKeyAndDefaultMappingsAreBlank() {
        ApiKeyProperties props = newProperties("");
        props.setEnabled(true);
        props.setCoreUrl("http://core");
        props.setRolesMapping("");
        props.setStartupProbe(false);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roles-mapping");
    }

    @Test
    void shouldRejectWhenBothApiKeyAndDefaultMappingsAreEmptyObjects() {
        ApiKeyProperties props = newProperties("{}");
        props.setEnabled(true);
        props.setCoreUrl("http://core");
        props.setRolesMapping("{}");
        props.setStartupProbe(false);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roles-mapping");
    }

    @Test
    void shouldParseApiKeyRolesMapping() {
        ApiKeyProperties props = newProperties("");
        props.setEnabled(true);
        props.setCoreUrl("http://core");
        props.setRolesMapping("{\"admin\":[\"FULL_ADMIN\"]}");
        props.setStartupProbe(false);
        props.validate();
        assertThat(props.getParsedRolesMapping()).containsKey("admin");
        assertThat(props.getParsedDefaultRolesMapping()).isEmpty();
    }

    @Test
    void shouldAcceptBlankApiKeyMappingWhenDefaultMappingIsSet() {
        ApiKeyProperties props = newProperties("{\"sso-admin\":[\"FULL_ADMIN\"]}");
        props.setEnabled(true);
        props.setCoreUrl("http://core");
        props.setRolesMapping("");
        props.setStartupProbe(false);
        props.validate();
        assertThat(props.getParsedRolesMapping()).isEmpty();
        assertThat(props.getParsedDefaultRolesMapping()).containsKey("sso-admin");
    }

    @Test
    void shouldParseBothMappingsWhenProvided() {
        ApiKeyProperties props = newProperties("{\"sso-admin\":[\"FULL_ADMIN\"]}");
        props.setEnabled(true);
        props.setCoreUrl("http://core");
        props.setRolesMapping("{\"admin\":[\"FULL_ADMIN\"]}");
        props.setStartupProbe(false);
        props.validate();
        assertThat(props.getParsedRolesMapping()).containsKey("admin");
        assertThat(props.getParsedDefaultRolesMapping()).containsKey("sso-admin");
    }

    @Test
    void shouldRejectInvalidDefaultRolesMappingJson() {
        ApiKeyProperties props = newProperties("not-json");
        props.setEnabled(true);
        props.setCoreUrl("http://core");
        props.setRolesMapping("{\"admin\":[\"FULL_ADMIN\"]}");
        props.setStartupProbe(false);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default.roles-mapping");
    }

    private static ApiKeyProperties newProperties(String defaultRolesMappingJson) {
        return new ApiKeyProperties(new ObjectMapper(), defaultRolesMappingJson);
    }
}
