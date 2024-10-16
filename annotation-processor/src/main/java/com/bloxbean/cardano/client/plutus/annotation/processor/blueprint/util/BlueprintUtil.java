package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util;

public class BlueprintUtil {

    //Exp: cardano/transaction/OutputReference
    //Output: cardano/transaction
    public static String getNSFromReferenceKey(String key) {
        if (key == null)
            return "";
        String[] titleTokens = key.split("\\/");
        String ns = "";

        if (titleTokens.length > 1) {
            //Iterate titleTokens and create ns and remove last dot
            for (int i = 0; i < titleTokens.length - 1; i++) {
                ns += titleTokens[i] + ".";
            }
            ns = ns.substring(0, ns.length() - 1);
        }

        if (ns != null && !ns.isEmpty()) {
            ns = ns.toLowerCase();
        }
        return ns;
    }

    //Exp: #/definitions/types~1automatic_payments~1AutomatedPayment
    //Output: types/automatic_payments
    public static String getNSFromReference(String ref) {
        if (ref == null)
            return "";
        ref = ref.replace("#/definitions/", "");
        ref = ref.replace("~1", "/");

        return getNSFromReferenceKey(ref);
    }

    public static String normalizedReference(String ref) {
        if (ref == null)
            return "";
        ref = ref.replace("#/definitions/", "");
        ref = ref.replace("~1", "/");
        return ref;
    }
}
