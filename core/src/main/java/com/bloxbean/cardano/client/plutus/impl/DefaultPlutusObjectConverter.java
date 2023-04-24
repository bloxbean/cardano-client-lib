package com.bloxbean.cardano.client.plutus.impl;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.api.PlutusObjectConverter;
import com.bloxbean.cardano.client.plutus.exception.PlutusDataConvertionException;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

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
            return BigIntPlutusData.of((Integer) obj);
        } else if (obj instanceof BigInteger) {
            return BigIntPlutusData.of((BigInteger) obj);
        } else if (obj instanceof Long) {
            return BigIntPlutusData.of((Long) obj);
        } else if (obj instanceof byte[]) {
            return BytesPlutusData.of((byte[]) obj);
        } else if (obj instanceof String) {
            return BytesPlutusData.of((String) obj);
        } else if (obj instanceof Optional) {
            return convertOptionalType((Optional<?>) obj);
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

    private ConstrPlutusData convertOptionalType(Optional<?> obj) {
        if (obj.isEmpty())
            return ConstrPlutusData.builder()
                    .alternative(1)
                    .data(ListPlutusData.of()).build();
        else
            return ConstrPlutusData.builder()
                    .alternative(0)
                    .data(ListPlutusData.of(toPlutusData(obj.get())))
                    .build();
    }

    private PlutusData _toPlutusData(String fieldName, Object obj) {
        if (Objects.isNull(obj)) {
            throw new PlutusDataConvertionException("Can't convert a null object. Field : " + fieldName);
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

        } else if (Map.class.isAssignableFrom(clazz)) {
            Map map = (Map) obj;
            plutusData = new MapPlutusData();

            Iterator<Map.Entry> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = iterator.next();
                PlutusData keyPlutusData = toPlutusData(entry.getKey());
                PlutusData valuePlutusData = toPlutusData(entry.getValue());

                ((MapPlutusData) plutusData).put(keyPlutusData, valuePlutusData);
            }
        } else {
            plutusData = toPlutusData(obj);

//          log.error("Valid field types: String, byte[], BigInteger, Long, Integer");
//          throw new PlutusDataConvertionException("Unsupported field type : name: " + field.getName() + ", type: " + field.getType());
        }

        return plutusData;

    }

    @Override
    public <T> T fromPlutusData(ConstrPlutusData plutusData, Class<T> tClass) {
//        return null;
//
//        if (plutusData instanceof BigIntPlutusData) {
//            return ((BigIntPlutusData) plutusData).getValue();
//        } else if (obj instanceof Integer) {
//            return BigIntPlutusData.of((Integer) obj);
//        } else if (obj instanceof BigInteger) {
//            return BigIntPlutusData.of((BigInteger) obj);
//        } else if (obj instanceof Long) {
//            return BigIntPlutusData.of((Long) obj);
//        } else if (obj instanceof byte[]) {
//            return BytesPlutusData.of((byte[]) obj);
//        } else if (obj instanceof String) {
//            return BytesPlutusData.of((String) obj);
//        }


        T obj = null;
        try {
            obj = tClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new PlutusDataConvertionException("Can't create a new instance of class : " + tClass.getName(), e);
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

        if (alternative != plutusData.getAlternative())
            throw new PlutusDataConvertionException(format("Alternative (%s) in @Constr doesn't match with alternative (%s) PlutusData",
                    alternative, plutusData.getAlternative()));

        ListPlutusData listData = plutusData.getData();
        if (listData.getPlutusDataList().size() != fields.size())
            throw new PlutusDataConvertionException(format("No of PlutusFields in class (%s) doesn't match with PlutusData", fields.size()));


        try {
            for (int i = 0; i < listData.getPlutusDataList().size(); i++) {
                PlutusData pData = listData.getPlutusDataList().get(i);
                Tuple<Field, PlutusField> fieldTuple = fields.get(i);

                fieldTuple._1.set(obj, getValue(pData, fieldTuple._1));

            }

            return obj;
        } catch (Exception e) {
            throw new PlutusDataConvertionException("PlutusData --> Object conversion error", e);
        }
    }


    private Object decodeValue(PlutusData plutusData, Field field) {
        Class clazz = field.getType();
        if (clazz == byte[].class) {
            return ((BytesPlutusData) plutusData).getValue();
        } else if (clazz == String.class) {
            byte[] bytes = ((BytesPlutusData) plutusData).getValue();
            String value = new String(bytes);
            //TODO -- Hex ??
        } else if (clazz == BigInteger.class || clazz == Long.class || clazz == Integer.class) {
            BigInteger bi = ((BigIntPlutusData) plutusData).getValue();

            if (clazz == BigInteger.class) {
                return bi;
            } else if (clazz == Long.class) {
                return bi.longValue();
            } else if (clazz == Integer.class) {
                return bi.intValue();
            }

        } else if (Collection.class.isAssignableFrom(clazz)) {
            ParameterizedType stringListType = (ParameterizedType) field.getGenericType();
            Class<?> itemClazz = (Class<?>) stringListType.getActualTypeArguments()[0];


        }
//        else if (Map.class.isAssignableFrom(clazz)) {
//            Map map = (Map) obj;
//
//            plutusData = new MapPlutusData();
//            for (Object key : map.keySet()) {
//                Object value = map.get(key);
//                PlutusData keyPlutusData = toPlutusData(key);
//                PlutusData valuePlutusData = toPlutusData(value);
//
//                ((MapPlutusData) plutusData).put(keyPlutusData, valuePlutusData);
//            }
//        }
    }

    private Object getValue(@NonNull PlutusData plutusData, Class clazz) {
        if (plutusData instanceof BigIntPlutusData)
            return ((BigIntPlutusData) plutusData).getValue();
        else if (plutusData instanceof BytesPlutusData)
            return ((BytesPlutusData) plutusData).getValue();
        else if (plutusData instanceof ListPlutusData) {
            return ((ListPlutusData) plutusData).getPlutusDataList()
                    .stream().map(pData -> getValue(pData))
                    .collect(Collectors.toList());
        } else if (plutusData instanceof MapPlutusData) {
            return ((MapPlutusData) plutusData).getMap().entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            entry -> getValue(entry.getKey()),
                            entry -> getValue(entry.getValue())));
        } else if (plutusData instanceof ConstrPlutusData) {
            return fromPlutusData((ConstrPlutusData) plutusData, clazz);
        }
    }

    private Object getSingleValue(@NonNull PlutusData plutusData) {
        if (plutusData instanceof BigIntPlutusData)
            return ((BigIntPlutusData) plutusData).getValue();
        else if (plutusData instanceof BytesPlutusData)
            return ((BytesPlutusData) plutusData).getValue();
        else
            throw new PlutusDataConvertionException("Expected BigIntPlutusData or BytesPlutusData, but found " + plutusData.getClass());
    }

    private Object getListValue(@NonNull ListPlutusData plutusData, Class clazz) {
        return ((ListPlutusData) plutusData).getPlutusDataList()
                .stream().map(pData -> getValue(pData))
                .collect(Collectors.toList());
    }

    private Object getValue(@NonNull PlutusData plutusData, Field field) {
        if (plutusData instanceof BigIntPlutusData)
            return ((BigIntPlutusData) plutusData).getValue();
        else if (plutusData instanceof BytesPlutusData)
            return ((BytesPlutusData) plutusData).getValue();
        else if (plutusData instanceof ListPlutusData) {
            ParameterizedType stringListType = (ParameterizedType) field.getGenericType();
            Class<?> clazz = (Class<?>) stringListType.getActualTypeArguments()[0];
            return ((ListPlutusData) plutusData).getPlutusDataList()
                    .stream().map(pData -> getValue(pData))
                    .collect(Collectors.toList());
        } else if (plutusData instanceof MapPlutusData) {
            return ((MapPlutusData) plutusData).getMap().entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            entry -> getValue(entry.getKey()),
                            entry -> getValue(entry.getValue())));
        } else if (plutusData instanceof ConstrPlutusData) {
            return fromPlutusData((ConstrPlutusData) plutusData, clazz);
        } else
            throw new PlutusDataConvertionException("Invalid PlutusData, " + plutusData);
    }
}
