package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil.toPackageNameFormat;

/**
 * Resolves target packages for generated sources to keep naming consistent.
 * This mirrors existing logic to avoid any behavior change.
 */
public class PackageResolver {

    /**
     * Model package used for generated Datum/Schema types.
     * Format: {@code annotation.packageName + ["." + ns] + ".model"}
     *
     * @param annotation the blueprint annotation containing base package name
     * @param ns the namespace to append (can be null or empty)
     * @return the resolved package name for model classes
     */
    public String getModelPackage(Blueprint annotation, String ns) {
        String pkg = (ns != null && !ns.isEmpty())
                ? annotation.packageName() + "." + ns + ".model"
                : annotation.packageName() + ".model";

        return toPackageNameFormat(pkg);
    }

    /**
     * Validator package derived from validator title.
     * Current behavior: takes only the first token before a dot as suffix
     * (e.g., {@code "basic.always_true"} becomes package suffix {@code "basic"}).
     *
     * @param annotation the blueprint annotation containing base package name
     * @param validatorTitle the validator title from the blueprint
     * @return the resolved package name for the validator class
     */
    public String getValidatorPackage(Blueprint annotation, String validatorTitle) {
        String pkgSuffix = null;
        if (validatorTitle != null) {
            String[] titleTokens = validatorTitle.split("\\.");
            if (titleTokens.length > 1) {
                pkgSuffix = titleTokens[0];
            }
        }

        String packageName = annotation.packageName();
        if (pkgSuffix != null)
            packageName = packageName + "." + pkgSuffix;

        return toPackageNameFormat(packageName);
    }

    /**
     * Namespace portion used for inline schemas derived from the validator title.
     *
     * @param validatorTitle the validator title from the blueprint
     * @return the namespace extracted from the validator title, or null if not applicable
     */
    public String getValidatorNamespace(String validatorTitle) {
        if (validatorTitle == null || validatorTitle.isEmpty())
            return null;

        String[] titleTokens = validatorTitle.split("\\.");
        if (titleTokens.length > 1) {
            return titleTokens[0];
        }
        return null;
    }
}
