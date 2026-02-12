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
     * <p><b>Namespace Handling:</b></p>
     * <ul>
     *   <li>Types WITH module paths: {@code annotation.packageName + "." + namespace + ".model"}</li>
     *   <li>Types WITHOUT module paths: {@code annotation.packageName + ".model"} (no namespace component)</li>
     * </ul>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>{@code "types/order/OrderDatum"} → namespace "types.order" → {@code com.example.blueprint.types.order.model}</li>
     *   <li>{@code "Bool"} (root-level ADT) → namespace "" → {@code com.example.blueprint.model}</li>
     * </ul>
     *
     * <p><b>Note:</b> Most types without module paths are filtered before reaching this method:</p>
     * <ul>
     *   <li>Generic instantiations: {@code "Option<T>"}, {@code "List<T>"} - skipped (contain &lt; &gt; $)</li>
     *   <li>Primitives: {@code "Int"}, {@code "ByteArray"} - classified as ALIAS and skipped</li>
     *   <li>Abstract types: {@code "Data"} - classified as ALIAS and skipped</li>
     * </ul>
     * Only legitimate root-level custom types (like {@code "Bool"}) reach here with empty namespace.
     *
     * @param annotation the blueprint annotation containing base package name
     * @param ns the namespace to append (can be null or empty)
     * @return the resolved package name for model classes
     */
    public String getModelPackage(Blueprint annotation, String ns) {
        // For types WITH module paths: annotation.packageName() + "." + namespace + ".model"
        // For types WITHOUT module paths (empty namespace): annotation.packageName() + ".model"
        //
        // Examples:
        //   "types/order/OrderDatum" → namespace "types.order" → com.example.blueprint.types.order.model
        //   "Bool" (root-level ADT) → namespace "" → com.example.blueprint.model
        //
        // Note: Most types without paths are filtered BEFORE reaching this method:
        //   1. Generic instantiations (Option<T>, List<T>) → skipped (contain < > $)
        //   2. Primitives (Int, ByteArray) → classified as ALIAS and skipped
        //   3. Abstract types (Data) → classified as ALIAS and skipped
        //
        // Only legitimate root-level custom types (like Bool) reach here with empty namespace.
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
