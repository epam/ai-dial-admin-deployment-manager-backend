package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.Scaling;
import com.epam.aidial.deployment.manager.model.ScalingStrategy;
import com.epam.aidial.deployment.manager.model.ScalingStrategyType;
import com.epam.aidial.deployment.manager.web.dto.ScalingDto;
import com.epam.aidial.deployment.manager.web.dto.ScalingStrategyDto;
import com.epam.aidial.deployment.manager.web.dto.ScalingStrategyTypeDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ScalingDtoMapper {

    Scaling toScaling(ScalingDto dto);

    ScalingDto toScalingDto(Scaling model);

    ScalingStrategy toScalingStrategy(ScalingStrategyDto dto);

    ScalingStrategyDto toScalingStrategyDto(ScalingStrategy model);

    ScalingStrategyType toScalingStrategyType(ScalingStrategyTypeDto dto);

    ScalingStrategyTypeDto toScalingStrategyTypeDto(ScalingStrategyType model);
}
