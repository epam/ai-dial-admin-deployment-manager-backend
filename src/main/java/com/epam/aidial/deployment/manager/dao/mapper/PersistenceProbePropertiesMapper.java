package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.entity.probe.PersistenceExecProbe;
import com.epam.aidial.deployment.manager.dao.entity.probe.PersistenceGrpcProbe;
import com.epam.aidial.deployment.manager.dao.entity.probe.PersistenceHttpGetProbe;
import com.epam.aidial.deployment.manager.dao.entity.probe.PersistenceProbeHandler;
import com.epam.aidial.deployment.manager.dao.entity.probe.PersistenceProbeProperties;
import com.epam.aidial.deployment.manager.dao.entity.probe.PersistenceTcpSocketProbe;
import com.epam.aidial.deployment.manager.model.probe.ExecProbe;
import com.epam.aidial.deployment.manager.model.probe.GrpcProbe;
import com.epam.aidial.deployment.manager.model.probe.HttpGetProbe;
import com.epam.aidial.deployment.manager.model.probe.ProbeHandler;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.model.probe.TcpSocketProbe;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(componentModel = "spring", subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface PersistenceProbePropertiesMapper {

    ProbeProperties toProbeProperties(PersistenceProbeProperties entity);

    PersistenceProbeProperties toPersistenceProbeProperties(ProbeProperties domain);

    @SubclassMapping(source = PersistenceHttpGetProbe.class, target = HttpGetProbe.class)
    @SubclassMapping(source = PersistenceTcpSocketProbe.class, target = TcpSocketProbe.class)
    @SubclassMapping(source = PersistenceExecProbe.class, target = ExecProbe.class)
    @SubclassMapping(source = PersistenceGrpcProbe.class, target = GrpcProbe.class)
    ProbeHandler toProbeHandler(PersistenceProbeHandler entity);

    @SubclassMapping(source = HttpGetProbe.class, target = PersistenceHttpGetProbe.class)
    @SubclassMapping(source = TcpSocketProbe.class, target = PersistenceTcpSocketProbe.class)
    @SubclassMapping(source = ExecProbe.class, target = PersistenceExecProbe.class)
    @SubclassMapping(source = GrpcProbe.class, target = PersistenceGrpcProbe.class)
    PersistenceProbeHandler toPersistenceProbeHandler(ProbeHandler domain);
}
