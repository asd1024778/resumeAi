package com.resume.resumeio;

import com.resume.resumeio.util.SensitiveDataMask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataMaskTest {

    @Test
    void shouldMaskPhoneAndIdCard() {
        assertEquals("138****5678", SensitiveDataMask.maskPhone("13812345678"));
        assertEquals("110101********1234", SensitiveDataMask.maskIdCard("110101199001011234"));
    }

    @Test
    void shouldMaskNameEmailAndAddress() {
        assertEquals("张*", SensitiveDataMask.maskName("张三"));
        assertEquals("d***@example.com", SensitiveDataMask.maskEmail("demo@example.com"));
        assertTrue(SensitiveDataMask.maskAddress("北京市朝阳区建国路88号").startsWith("北京市朝阳区"));
    }

    @Test
    void shouldMaskSocialAccount() {
        assertEquals("wec****024", SensitiveDataMask.maskSocialAccount("wechat_demo_2024"));
    }
}
