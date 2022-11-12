package com.bloxbean.cardano.client.transaction.spec;

import lombok.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class ProtocolVersion {
    private int major;
    private int minor;
}
