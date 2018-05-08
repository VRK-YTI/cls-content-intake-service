package fi.vm.yti.codelist.intake.log;

import fi.vm.yti.codelist.intake.model.Code;
import fi.vm.yti.codelist.intake.model.CodeRegistry;
import fi.vm.yti.codelist.intake.model.CodeScheme;
import fi.vm.yti.codelist.intake.model.Extension;
import fi.vm.yti.codelist.intake.model.ExtensionScheme;
import fi.vm.yti.codelist.intake.model.ExternalReference;
import fi.vm.yti.codelist.intake.model.PropertyType;

public interface EntityChangeLogger {

    void logCodeRegistryChange(final CodeRegistry codeRegistry);

    void logCodeSchemeChange(final CodeScheme codeScheme);

    void logCodeChange(final Code code);

    void logExternalReferenceChange(final ExternalReference externalReference);

    void logPropertyTypeChange(final PropertyType propertyType);

    void logExtensionSchemeChange(final ExtensionScheme extensionScheme);

    void logExtensionChange(final Extension extension);
}
