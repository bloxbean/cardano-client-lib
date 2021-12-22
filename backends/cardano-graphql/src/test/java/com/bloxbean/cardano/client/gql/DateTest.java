package com.bloxbean.cardano.client.gql;

import com.bloxbean.cardano.client.backend.gql.util.DateUtil;
import org.junit.jupiter.api.Test;

import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DateTest {

    @Test
    public void testDate() throws ParseException {
        String dateStr = "2021-07-07T07:38:53Z";
        long time = DateUtil.convertDateTimeToLong(dateStr);
        System.out.println(time);
        //assertEquals("1625643533000", time);
    }
}
