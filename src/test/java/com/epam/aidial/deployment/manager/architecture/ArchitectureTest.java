package com.epam.aidial.deployment.manager.architecture;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

@AnalyzeClasses(
        packages = "com.epam.aidial.deployment.manager",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // -------------------------------------------------------------------------
    // Strict Layered Architecture
    // Existing violations are frozen — only NEW violations will cause failures.
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule webLayerMustNotAccessDao = noClasses()
            .that().resideInAPackage("..web..")
            .should().accessClassesThat().resideInAPackage("..dao..")
            .because("the web layer must not directly access the data access layer — use the service layer instead");

    @ArchTest
    static final ArchRule webLayerMustNotAccessKubernetes = FreezingArchRule.freeze(noClasses()
            .that().resideInAPackage("..web..")
            .should().accessClassesThat().resideInAPackage("..kubernetes..")
            .because("the web layer must not directly access the kubernetes layer — use the service layer instead"));

    @ArchTest
    static final ArchRule daoLayerMustNotAccessService = noClasses()
            .that().resideInAPackage("..dao..")
            .should().accessClassesThat().resideInAPackage("..service..")
            .because("the dao layer must not depend on the service layer — dependency direction is service → dao");

    @ArchTest
    static final ArchRule daoLayerMustNotAccessWeb = FreezingArchRule.freeze(noClasses()
            .that().resideInAPackage("..dao..")
            .should().accessClassesThat().resideInAPackage("..web..")
            .because("the dao layer must not depend on the web layer — dependency direction is web → service → dao"));

    @ArchTest
    static final ArchRule kubernetesLayerMustNotAccessService = FreezingArchRule.freeze(noClasses()
            .that().resideInAPackage("..kubernetes..")
            .should().accessClassesThat().resideInAPackage("..service..")
            .because("the kubernetes layer must not depend on the service layer — dependency direction is service → kubernetes"));

    @ArchTest
    static final ArchRule kubernetesLayerMustNotAccessWeb = noClasses()
            .that().resideInAPackage("..kubernetes..")
            .should().accessClassesThat().resideInAPackage("..web..")
            .because("the kubernetes layer must not depend on the web layer");

    // -------------------------------------------------------------------------
    // Transactional Discipline
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule restControllersMustNotBeTransactional = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().beAnnotatedWith(Transactional.class)
            .because("@Transactional must only appear on service-layer or dao-layer classes, not on controllers");

    @ArchTest
    static final ArchRule restControllerMethodsMustNotBeTransactional = noMethods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should().beAnnotatedWith(Transactional.class)
            .because("@Transactional must only appear on service-layer or dao-layer methods, not on controller methods");

    // -------------------------------------------------------------------------
    // Observability — @LogExecution on every Spring component
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule servicesMustHaveLogExecution = classes()
            .that().areAnnotatedWith(Service.class)
            .should().beAnnotatedWith(LogExecution.class)
            .because("@LogExecution must be placed on every @Service class");

    @ArchTest
    static final ArchRule restControllersMustHaveLogExecution = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().beAnnotatedWith(LogExecution.class)
            .because("@LogExecution must be placed on every @RestController class");

    @ArchTest
    static final ArchRule componentsMustHaveLogExecution = classes()
            .that().areAnnotatedWith(Component.class)
            .and().haveSimpleNameNotEndingWith("MapperImpl")
            .and().haveSimpleNameNotEndingWith("Properties")
            .and().resideOutsideOfPackage("..configuration..")
            .and().areNotAnnotatedWith(Aspect.class)
            .should().beAnnotatedWith(LogExecution.class)
            .because("@LogExecution must be placed on every @Component class");

    @ArchTest
    static final ArchRule repositoriesMustHaveLogExecution = classes()
            .that().areAnnotatedWith(Repository.class)
            .and().areNotInterfaces()
            .should().beAnnotatedWith(LogExecution.class)
            .because("@LogExecution must be placed on every @Repository class");

    // -------------------------------------------------------------------------
    // Naming Conventions
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule entitiesMustResideInDaoPackage = classes()
            .that().haveSimpleNameEndingWith("Entity")
            .should().resideInAPackage("..dao..")
            .because("JPA entity classes must reside in the dao package");

    @ArchTest
    static final ArchRule jpaRepositoriesMustResideInDaoJpaPackage = classes()
            .that().haveSimpleNameEndingWith("JpaRepository")
            .should().resideInAPackage("..dao.jpa..")
            .because("Spring Data JPA repository interfaces must reside in the dao.jpa package");

    @ArchTest
    static final ArchRule domainRepositoriesMustResideInDaoRepositoryPackage = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .and().haveSimpleNameNotEndingWith("JpaRepository")
            .and().areAnnotatedWith(Repository.class)
            .should().resideInAPackage("..dao.repository..")
            .because("DAO repository wrapper classes must reside in the dao.repository package");

    @ArchTest
    static final ArchRule dtoMappersMustResideInWebMapperPackage = classes()
            .that().haveSimpleNameEndingWith("DtoMapper")
            .should().resideInAPackage("..web.mapper..")
            .because("Web DTO mapper interfaces must reside in the web.mapper package");

    @ArchTest
    static final ArchRule persistenceMappersMustResideInDaoMapperPackage = classes()
            .that().haveSimpleNameStartingWith("Persistence")
            .and().haveSimpleNameEndingWith("Mapper")
            .should().resideInAPackage("..dao.mapper..")
            .because("Persistence mapper interfaces must reside in the dao.mapper package");

    // -------------------------------------------------------------------------
    // MapStruct — componentModel = "spring" required on all @Mapper interfaces
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule mappersMustUseSpringComponentModel = classes()
            .that().areAnnotatedWith("org.mapstruct.Mapper")
            .should(new ArchCondition<>("declare @Mapper(componentModel = \"spring\")") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    item.tryGetAnnotationOfType("org.mapstruct.Mapper")
                            .ifPresent(annotation -> {
                                var componentModel = annotation.get("componentModel");
                                if (componentModel.isEmpty() || !"spring".equals(componentModel.get())) {
                                    events.add(SimpleConditionEvent.violated(item, item.getDescription() + " does not declare @Mapper(componentModel = \"spring\")"));
                                }
                            });
                }
            })
            .because("all MapStruct mappers must declare componentModel = \"spring\" to integrate with the Spring context");

    // -------------------------------------------------------------------------
    // Anti-Patterns
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule noSystemOutOrErrUsage = noClasses()
            .should().accessField(System.class, "out")
            .orShould().accessField(System.class, "err")
            .because("use SLF4J (@Slf4j) for logging instead of System.out / System.err");
}
