package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.NameStrategy;

/**
 * Base implementation shared by datatype processors providing access to the naming
 * strategy and schema type resolver utilities.
 */
abstract class AbstractDataTypeProcessor implements DataTypeProcessor {

    protected final NameStrategy nameStrategy;
    protected final SchemaTypeResolver typeResolver;

    protected AbstractDataTypeProcessor(NameStrategy nameStrategy, SchemaTypeResolver typeResolver) {
        this.nameStrategy = nameStrategy;
        this.typeResolver = typeResolver;
    }

    protected String resolveFieldName(DataTypeProcessingContext context) {
        String title = context.getSchema().getTitle() == null
                ? context.getAlternativeName()
                : context.getSchema().getTitle();

        return nameStrategy.firstLowerCase(nameStrategy.toCamelCase(title));
    }
}
