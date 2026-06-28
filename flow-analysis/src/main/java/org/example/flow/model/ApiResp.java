package org.example.flow.model;

public class ApiResp<T> {

    private int    code;
    private String message;
    private T      data;

    private ApiResp(int code, String message, T data) {
        this.code    = code;
        this.message = message;
        this.data    = data;
    }

    public static <T> ApiResp<T> ok(T data) {
        return new ApiResp<>(200, "success", data);
    }

    public static <T> ApiResp<T> error(int code, String msg) {
        return new ApiResp<>(code, msg, null);
    }

    public int    getCode()    { return code; }
    public String getMessage() { return message; }
    public T      getData()    { return data; }

    public void setCode(int code)       { this.code    = code; }
    public void setMessage(String msg)  { this.message = msg; }
    public void setData(T data)         { this.data    = data; }
}
