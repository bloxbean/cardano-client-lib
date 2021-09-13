package com.bloxbean.cardano.client.util;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.exception.AddressExcepion;

public class AddressUtil {

    /**
     * Check if shelley or byron addresses are valid
     *
     * @param addr - shelley or byron era address
     * @return true if address is valid, false otherwise
     */
    public static boolean isValidAddress(String addr) {
        try {
            Account.toBytes(addr);

            return true;
        } catch (AddressExcepion e) {
            return false;
        }
    }

}
