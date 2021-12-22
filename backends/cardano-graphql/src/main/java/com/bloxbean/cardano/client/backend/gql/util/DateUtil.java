package com.bloxbean.cardano.client.backend.gql.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateUtil {

    public static long convertDateTimeToLong(String dateTimeInGMT) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            inputFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date dt = inputFormat.parse(dateTimeInGMT);
            return dt.getTime();
        } catch (Exception e) {
            return 0;
        }
    }
}
