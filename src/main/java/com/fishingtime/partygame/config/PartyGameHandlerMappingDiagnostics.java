package com.fishingtime.partygame.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Temporary startup diagnostics for the PartyGame WebSocket endpoint.
 * This component only inspects registered HandlerMappings; it does not
 * change routing, register handlers, or expose an HTTP endpoint.
 */
@Component
public class PartyGameHandlerMappingDiagnostics implements ApplicationRunner {
    private static final Logger log =
            LoggerFactory.getLogger(PartyGameHandlerMappingDiagnostics.class);

    private static final String TARGET_PATH = "/ws/partygame";

    private final ApplicationContext applicationContext;

    public PartyGameHandlerMappingDiagnostics(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== PartyGame HandlerMapping Diagnostics: START ==========");

        Map<String, HandlerMapping> mappings =
                applicationContext.getBeansOfType(HandlerMapping.class);

        boolean endpointFound = false;

        for (Map.Entry<String, HandlerMapping> entry : mappings.entrySet()) {
            HandlerMapping mapping = entry.getValue();
            int order = mapping instanceof Ordered
                    ? ((Ordered) mapping).getOrder()
                    : Ordered.LOWEST_PRECEDENCE;

            log.info("[PartyGame-DIAG] HandlerMapping bean={}, type={}, order={}",
                    entry.getKey(), mapping.getClass().getName(), order);

            if (mapping instanceof AbstractUrlHandlerMapping) {
                Map<String, Object> handlerMap =
                        ((AbstractUrlHandlerMapping) mapping).getHandlerMap();

                for (Map.Entry<String, Object> handlerEntry : handlerMap.entrySet()) {
                    String pattern = handlerEntry.getKey();
                    Object handler = handlerEntry.getValue();

                    log.info("[PartyGame-DIAG] URL mapping bean={}, pattern={}, handler={}",
                            entry.getKey(), pattern, handler.getClass().getName());

                    if (TARGET_PATH.equals(pattern)
                            && handler.getClass().getName().contains("WebSocketHttpRequestHandler")) {
                        endpointFound = true;
                    }
                }
            }
        }

        log.info("[PartyGame-DIAG] targetPath={}, websocketHandlerRegistered={}",
                TARGET_PATH, endpointFound);
        log.info("========== PartyGame HandlerMapping Diagnostics: END ==========");
    }
}
