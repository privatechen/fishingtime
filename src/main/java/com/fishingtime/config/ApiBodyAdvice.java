package com.fishingtime.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * API 请求体日志
 *
 * 打印所有 @RequestBody 的 body（JSON 格式）。读取后重新包装，不影响目标方法继续读取。
 */
@Component
public class ApiBodyAdvice implements RequestBodyAdvice {

    private static final Logger log = LoggerFactory.getLogger(ApiBodyAdvice.class);

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
                                           Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        byte[] bytes = inputMessage.getBody().readAllBytes();
        String body = new String(bytes, StandardCharsets.UTF_8);
        // 与 ApiLogInterceptor 合并信息：一条日志包含方法/路径 + body
        String method = "";
        String uri = "";
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            method = request.getMethod();
            uri = request.getRequestURI();
        }
        log.info("[API入参] {} {} body={}", method, uri, body);
        // 包装缓存后的 body，保证目标方法仍能读取
        return new ByteArrayHttpInputMessage(bytes, inputMessage.getHeaders());
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                  Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    /** 缓存 body 的输入消息，保证"只读一次"后仍可被读取 */
    private static class ByteArrayHttpInputMessage implements HttpInputMessage {
        private final byte[] body;
        private final HttpHeaders headers;

        ByteArrayHttpInputMessage(byte[] body, HttpHeaders headers) {
            this.body = body;
            this.headers = headers;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
