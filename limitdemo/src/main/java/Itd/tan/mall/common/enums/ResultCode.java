package Itd.tan.mall.common.enums;

public enum ResultCode {

    SUCCESS(200, "成功"),
    ERROR(400, "服务器繁忙，请稍后重试"),
    LIMIT_ERROR(1003, "访问过于频繁，请稍后再试");
    private final Integer code;
    private final String message;
    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer code() {
        return this.code;
    }

    public String message() {
        return this.message;
    }
}
