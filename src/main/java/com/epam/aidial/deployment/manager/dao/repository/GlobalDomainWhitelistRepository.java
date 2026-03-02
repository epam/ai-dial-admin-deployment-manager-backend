package com.epam.aidial.deployment.manager.dao.repository;

import com.epam.aidial.deployment.manager.dao.entity.DomainWhitelistEntity;
import com.epam.aidial.deployment.manager.dao.jpa.DomainWhitelistJpaRepository;
import com.epam.aidial.deployment.manager.exception.GlobalDomainWhitelistNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class GlobalDomainWhitelistRepository {

    private final DomainWhitelistJpaRepository jpaRepository;

    public List<String> getAllowedDomains() {
        var whitelist = getGlobalDomainWhitelist();
        return whitelist.getAllowedDomains();
    }

    public List<String> updateAllowedDomains(List<String> allowedDomains) {
        var whitelist = getGlobalDomainWhitelist();
        whitelist.setAllowedDomains(allowedDomains);
        var updatedWhitelist = jpaRepository.saveAndFlush(whitelist);
        return updatedWhitelist.getAllowedDomains();
    }

    public void setAllowedDomainsOrCreate(List<String> allowedDomains) {
        var entities = jpaRepository.findAll();
        DomainWhitelistEntity whitelist;
        if (CollectionUtils.isEmpty(entities)) {
            whitelist = new DomainWhitelistEntity();
            whitelist.setAllowedDomains(allowedDomains);
        } else {
            if (entities.size() > 1) {
                throw new IllegalStateException("More than 1 global domain whitelist found");
            }
            whitelist = entities.getFirst();
            whitelist.setAllowedDomains(allowedDomains);
        }
        jpaRepository.saveAndFlush(whitelist);
    }

    private DomainWhitelistEntity getGlobalDomainWhitelist() {
        var entities = jpaRepository.findAll();
        if (CollectionUtils.isEmpty(entities)) {
            throw new GlobalDomainWhitelistNotFoundException();
        }
        if (entities.size() > 1) {
            throw new IllegalStateException("More than 1 global domain whitelist found");
        }
        return entities.getFirst();
    }
}
