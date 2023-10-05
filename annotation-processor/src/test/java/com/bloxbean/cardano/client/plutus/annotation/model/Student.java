package com.bloxbean.cardano.client.plutus.annotation.model;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Constr(alternative = 1)
@Data
public class Student {
    private String name;
    private int age;
    private String gender;
    private List<Subject> subjects;
    private Optional<String> hobby;
    private boolean graduated;
    private Boolean fullTime;
    private Map<Subject, Integer> marks;
    private Optional<Boolean> cardanoHolder;
}
