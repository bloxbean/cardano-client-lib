package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support;

import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;

/**
 * Naming strategy wrapper to centralize class and identifier naming.
 * Currently delegates to existing JavaFileUtil to avoid behavior changes.
 */
public class NameStrategy {

    public String toClassName(String value) {
        return JavaFileUtil.toClassNameFormat(value);
    }

    public String toCamelCase(String value) {
        return JavaFileUtil.toCamelCase(value);
    }

    public String firstUpperCase(String value) {
        return JavaFileUtil.firstUpperCase(value);
    }

    public String firstLowerCase(String value) {
        return JavaFileUtil.firstLowerCase(value);
    }
}

