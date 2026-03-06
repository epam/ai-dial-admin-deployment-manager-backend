package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.config.ExportConfigComponentType;
import com.epam.aidial.deployment.manager.model.config.ExportRequest;
import com.epam.aidial.deployment.manager.model.config.SelectedItemsExportRequest;
import com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentDto;
import com.epam.aidial.deployment.manager.web.dto.config.ExportConfigComponentTypeDto;
import com.epam.aidial.deployment.manager.web.dto.config.SelectedItemsExportRequestDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ExportConfigMapperImpl.class)
class ExportConfigMapperTest {

    @Autowired
    private ExportConfigMapper exportConfigMapper;

    @Test
    void toExportRequest_selectedItemsDto_mapsToSelectedItemsExportRequest() {
        // Given
        SelectedItemsExportRequestDto dto = new SelectedItemsExportRequestDto();
        dto.setAddSecrets(true);
        dto.setAddGlobalImageBuildDomainWhitelist(true);
        dto.setComponents(List.of(
                new ExportConfigComponentDto("name-1", ExportConfigComponentTypeDto.MCP_IMAGE_DEFINITION),
                new ExportConfigComponentDto("dep-id", ExportConfigComponentTypeDto.MCP_DEPLOYMENT)
        ));

        // When
        ExportRequest result = exportConfigMapper.toExportRequest(dto);

        // Then
        assertThat(result).isInstanceOf(SelectedItemsExportRequest.class);
        SelectedItemsExportRequest selected = (SelectedItemsExportRequest) result;
        assertThat(selected.isAddSecrets()).isTrue();
        assertThat(selected.isAddGlobalImageBuildDomainWhitelist()).isTrue();
        assertThat(selected.getComponents()).hasSize(2);
        assertThat(selected.getComponents().get(0).getName()).isEqualTo("name-1");
        assertThat(selected.getComponents().get(0).getType()).isEqualTo(ExportConfigComponentType.MCP_IMAGE_DEFINITION);
        assertThat(selected.getComponents().get(1).getName()).isEqualTo("dep-id");
        assertThat(selected.getComponents().get(1).getType()).isEqualTo(ExportConfigComponentType.MCP_DEPLOYMENT);
    }
}
