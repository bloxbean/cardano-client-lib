package com.bloxbean.cardano.client.plutus.impl;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.api.PlutusObjectConverter;
import com.bloxbean.cardano.client.plutus.exception.PlutusDataConvertionException;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class DefaultPlutusObjectConverter implements PlutusObjectConverter {

    @Override
    public PlutusData toPlutusData(Object obj) {
        if (Objects.isNull(obj)) {
            throw new PlutusDataConvertionException("Can't convert a null object");
        }

        if (obj instanceof PlutusData) {
            return (PlutusData) obj;
        } else if (obj instanceof Integer) {
            return BigIntPlutusData.of((Integer)obj);
        } else if (obj instanceof BigInteger) {
            return BigIntPlutusData.of((BigInteger) obj);
        } else if (obj instanceof Long) {
            return BigIntPlutusData.of((Long)obj);
        } else if (obj instanceof byte[]) {
            return BytesPlutusData.of((byte[])obj);
        } else if (obj instanceof String) {
            return BytesPlutusData.of((String)obj);
        }

        Class<?> clazz = obj.getClass();

        int alternative;
        Constr constr = clazz.getAnnotation(Constr.class);
        if (constr != null) {
            alternative = constr.alternative();
        } else {
            throw new PlutusDataConvertionException("@Contr annotation not found in class : " + clazz.getName());
        }

        List<Tuple<Field, PlutusField>> fields = Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> field.getAnnotation(PlutusField.class) != null)
                .map(field -> new Tuple<>(field, field.getDeclaredAnnotation(PlutusField.class)))
                .collect(Collectors.toList());

        ListPlutusData listPlutusData = new ListPlutusData();
        ConstrPlutusData constrPlutusData = ConstrPlutusData.builder()
                .alternative(alternative)
                .data(listPlutusData)
                .build();

        for (Tuple<Field, PlutusField> tuple : fields) {
            Field field = tuple._1;
            field.setAccessible(true);

            Object value;
            try {
                value = field.get(obj);
            } catch (IllegalAccessException e) {
                throw new PlutusDataConvertionException("Unable to convert value for field : " + field.getName());
            }

            PlutusData plutusData = _toPlutusData(field.getName(), value);
            constrPlutusData.getData().add(plutusData);
        }

        return constrPlutusData;
    }

    private PlutusData _toPlutusData(String fieldName, Object obj) {
        if (Objects.isNull(obj)) {
            throw new PlutusDataConvertionException("Can't convert a null object : " + fieldName);
        }

        Class<?> clazz = obj.getClass();

        PlutusData plutusData = null;
        if (clazz == byte[].class) {
            plutusData = BytesPlutusData.of((byte[]) obj);
        } else if (clazz == String.class) {
            String value = (String) obj;
            if (value.startsWith("0x") || value.startsWith("0X")) {
                byte[] bytes = HexUtil.decodeHexString(value);
                plutusData = BytesPlutusData.of(bytes);
            } else {
                plutusData = BytesPlutusData.of(value);
            }
        } else if (clazz == BigInteger.class || clazz == Long.class || clazz == Integer.class) {
            Number value = (Number) obj;

            if (clazz == BigInteger.class) {
                plutusData = BigIntPlutusData.of((BigInteger) value);
            } else if (clazz == Long.class) {
                plutusData = BigIntPlutusData.of((Long) value);
            } else if (clazz == Integer.class) {
                plutusData = BigIntPlutusData.of((Integer) value);
            }

        } else if (Collection.class.isAssignableFrom(clazz)) {
            Collection values = (Collection) obj;

            plutusData = new ListPlutusData();
            for (Object value : values) {
                ((ListPlutusData) plutusData).add(_toPlutusData(fieldName + "[x]", value));
            }

        } else {
            plutusData = toPlutusData(obj);

//          log.error("Valid field types: String, byte[], BigInteger, Long, Integer");
//          throw new PlutusDataConvertionException("Unsupported field type : name: " + field.getName() + ", type: " + field.getType());
        }
        //TODO -- Handle map field

        return plutusData;

    }
}
