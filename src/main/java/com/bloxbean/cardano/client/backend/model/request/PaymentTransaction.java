package com.bloxbean.cardano.client.backend.model.request;

import com.bloxbean.cardano.client.account.Account;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {
    private Account sender;
    private String receiver;
    private String unit;
    private BigInteger amount;
    private BigInteger fee;
}
