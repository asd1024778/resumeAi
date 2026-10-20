package com.resume.resumeio.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文档解析服务：将上传的二进制文件转换为纯文本。
 * <p>
 * 业务职责：统一处理 PDF、Word、TXT 等格式的简历文件，
 * 提取可读文字供后续分块、RAG 入库与 AI 分析使用。
 * 底层依赖 Apache Tika，调用方无需关心具体文件格式。
 */
@Service
public class DocumentParserService {

    /** Tika 实例：Apache 通用文档内容提取引擎，线程安全可复用 */
    private final Tika tika = new Tika();

    /**
     * 从输入流中解析并提取纯文本内容。
     *
     * @param inputStream 文件二进制流（由 Controller 从 MultipartFile 获得）
     * @return 提取出的纯文本，例如 "姓名：张三\n学历：本科..."
     * @throws IOException     流读取失败
     * @throws TikaException   Tika 无法识别或解析该格式
     */
    public String parse(InputStream inputStream) throws IOException, TikaException {
        // Tika 自动检测 MIME 类型并调用对应解析器，返回 UTF-8 文本
        return tika.parseToString(inputStream);
    }
}
