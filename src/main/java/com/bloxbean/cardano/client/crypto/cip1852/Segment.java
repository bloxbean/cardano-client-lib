package com.bloxbean.cardano.client.crypto.cip1852;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Segment {
    private int value;
    private boolean isHarden;
}
