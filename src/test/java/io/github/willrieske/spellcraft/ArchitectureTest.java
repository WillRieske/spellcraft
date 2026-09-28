package io.github.willrieske.spellcraft;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Architecture fitness functions. A failure here means a boundary in the spec was crossed. */
class ArchitectureTest {
    static final String ROOT = "io.github.willrieske.spellcraft";

    static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void commonCodeNeverReferencesClientOnlyCode() {
        noClasses()
                .that()
                .resideOutsideOfPackage(ROOT + ".client..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "net.minecraft.client..",
                        "com.mojang.blaze3d..",
                        "net.neoforged.neoforge.client..",
                        ROOT + ".client..")
                .because("common classes load on dedicated servers, where client classes do not exist")
                .check(PRODUCTION);
    }

    @Test
    void upsideDownCoreIsPure() {
        classes()
                .that()
                .resideInAPackage(ROOT + ".client.upsidedown..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", ROOT + ".client.upsidedown..")
                .because("the pure core must be unit-testable without Minecraft or NeoForge")
                .check(PRODUCTION);
    }
}
