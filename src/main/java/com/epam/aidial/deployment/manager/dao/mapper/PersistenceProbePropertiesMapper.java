package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.entity.probe.PersistenceHttpGetProbe;
import com.epam.aidial.deployment.manager.dao.entity.probe.PersistenceProbeHandler;
import com.epam.aidial.deployment.manager.dao.entity.probe.PersistenceProbeProperties;
import com.epam.aidial.deployment.manager.model.probe.HttpGetProbe;
import com.epam.aidial.deployment.manager.model.probe.ProbeHandler;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(componentModel = "spring", subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface PersistenceProbePropertiesMapper {

    ProbeProperties toProbeProperties(PersistenceProbeProperties entity);

    PersistenceProbeProperties toPersistenceProbeProperties(ProbeProperties domain);

    @SubclassMapping(source = PersistenceHttpGetProbe.class, target = HttpGetProbe.class)
    ProbeHandler toProbeHandler(PersistenceProbeHandler entity);

    @SubclassMapping(source = HttpGetProbe.class, target = PersistenceHttpGetProbe.class)
    PersistenceProbeHandler toPersistenceProbeHandler(ProbeHandler domain);
}
