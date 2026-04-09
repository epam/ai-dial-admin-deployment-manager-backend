package com.epam.aidial.deployment.manager.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.epam.aidial.deployment.manager",
        importOptions = TestCodeArchitectureTest.OnlyIncludeTests.class)
class TestCodeArchitectureTest {

    static class OnlyIncludeTests implements ImportOption {
        private static final Pattern TEST_PATTERN = Pattern.compile(".*[\\\\/]test[\\\\/].*|.*[\\\\/]test-classes[\\\\/].*");

        @Override
        public boolean includes(Location location) {
            return location.matches(TEST_PATTERN);
        }
    }

    @ArchTest
    static final ArchRule testsMustUseAssertJInsteadOfJunitAssertions = noClasses()
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.junit.jupiter.api.Assertions")
            .because("use AssertJ assertions (org.assertj.core.api.Assertions) instead of JUnit Assertions");
}
