package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype;

import com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.NamingStrategy;

/**
 * Base implementation shared by datatype processors providing access to the naming
 * strategy and schema type resolver utilities.
 */
abstract class AbstractDataTypeProcessor implements DataTypeProcessor {

    protected final NamingStrategy nameStrategy;
    protected final SchemaTypeResolver typeResolver;

    protected AbstractDataTypeProcessor(NamingStrategy nameStrategy, SchemaTypeResolver typeResolver) {
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
