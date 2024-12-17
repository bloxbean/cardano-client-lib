package com.bloxbean.cardano.client.api.model;

public class Result<T> {
    boolean successful;
    String response;
    int code;
    T value;

    protected Result(boolean successful) {
        this.successful = successful;
    }

    protected Result(boolean successful, String response) {
        this.successful = successful;
        this.response = response;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public T getValue() {
        return value;
    }

    public Result withValue(T value) {
        this.value = value;
        return this;
    }

    public static Result error() {
        return new Result(false);
    }

    public static Result error(String response) {
        return new Result(false, response);
    }

    public static Result create(boolean status, String response) {
        return new Result(status, response);
    }

    public static Result success(String response) {
        return new Result(true, response);
    }

    public Result code(int code) {
        this.code = code;
        return this;
    }

    public int code() {
        return this.code;
    }

    @Override
    public String toString() {
        return "Result{" +
                "successful=" + successful +
                ", response='" + response + '\'' +
                ", code=" + code +
                ", value=" + value +
                '}';
    }
}

