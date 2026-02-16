package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.probe.HttpGetProbe;
import com.epam.aidial.deployment.manager.model.probe.ProbeHandler;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.web.dto.probe.HttpGetProbeDto;
import com.epam.aidial.deployment.manager.web.dto.probe.ProbeHandlerDto;
import com.epam.aidial.deployment.manager.web.dto.probe.ProbePropertiesDto;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(componentModel = "spring", subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface ProbePropertiesDtoMapper {

    ProbeProperties toProbeProperties(ProbePropertiesDto dto);

    ProbePropertiesDto toProbePropertiesDto(ProbeProperties model);

    @SubclassMapping(source = HttpGetProbeDto.class, target = HttpGetProbe.class)
    ProbeHandler toProbeHandler(ProbeHandlerDto dto);

    @SubclassMapping(source = HttpGetProbe.class, target = HttpGetProbeDto.class)
    ProbeHandlerDto toProbeHandlerDto(ProbeHandler model);
}
