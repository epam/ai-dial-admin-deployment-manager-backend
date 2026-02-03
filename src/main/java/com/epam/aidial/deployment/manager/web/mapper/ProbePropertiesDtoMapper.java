package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.probe.ExecProbe;
import com.epam.aidial.deployment.manager.model.probe.GrpcProbe;
import com.epam.aidial.deployment.manager.model.probe.HttpGetProbe;
import com.epam.aidial.deployment.manager.model.probe.ProbeHandler;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.model.probe.TcpSocketProbe;
import com.epam.aidial.deployment.manager.web.dto.probe.ExecProbeDto;
import com.epam.aidial.deployment.manager.web.dto.probe.GrpcProbeDto;
import com.epam.aidial.deployment.manager.web.dto.probe.HttpGetProbeDto;
import com.epam.aidial.deployment.manager.web.dto.probe.ProbeHandlerDto;
import com.epam.aidial.deployment.manager.web.dto.probe.ProbePropertiesDto;
import com.epam.aidial.deployment.manager.web.dto.probe.TcpSocketProbeDto;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(componentModel = "spring", subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface ProbePropertiesDtoMapper {

    ProbeProperties toProbeProperties(ProbePropertiesDto dto);

    ProbePropertiesDto toProbePropertiesDto(ProbeProperties model);

    @SubclassMapping(source = HttpGetProbeDto.class, target = HttpGetProbe.class)
    @SubclassMapping(source = TcpSocketProbeDto.class, target = TcpSocketProbe.class)
    @SubclassMapping(source = ExecProbeDto.class, target = ExecProbe.class)
    @SubclassMapping(source = GrpcProbeDto.class, target = GrpcProbe.class)
    ProbeHandler toProbeHandler(ProbeHandlerDto dto);

    @SubclassMapping(source = HttpGetProbe.class, target = HttpGetProbeDto.class)
    @SubclassMapping(source = TcpSocketProbe.class, target = TcpSocketProbeDto.class)
    @SubclassMapping(source = ExecProbe.class, target = ExecProbeDto.class)
    @SubclassMapping(source = GrpcProbe.class, target = GrpcProbeDto.class)
    ProbeHandlerDto toProbeHandlerDto(ProbeHandler model);
}
