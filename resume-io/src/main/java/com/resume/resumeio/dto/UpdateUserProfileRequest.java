package com.resume.resumeio.dto;

import lombok.Data;

/**
 * 更新用户敏感资料的请求体。
 * <p>
 * 对应用户资料修改接口，所有字段均为可选，仅更新传入的非空字段。
 */
@Data
public class UpdateUserProfileRequest {

    /** 真实姓名 */
    private String realName;

    /** 手机号码 */
    private String phone;

    /** 身份证号码 */
    private String idCard;

    /** 联系地址 */
    private String address;

    /** 社交账号（微信、QQ 等） */
    private String socialAccount;
}
