package com.saed.backend.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Asynchronous e-mails must run on the application's task executor. CompletableFuture.runAsync without an executor
 * uses the JDK common pool, whose threads do not carry Spring Boot's class loader inside the packaged jar: Thymeleaf
 * then cannot resolve classpath templates and the credentials e-mail of every account created by an administrator
 * was silently lost.
 */
class EmailAsyncExecutorTest {

    @Test
    void asyncMailRunsOnTheInjectedExecutor() {
        List<Runnable> submitted = new ArrayList<>();
        Executor recording = submitted::add;
        EmailService service = new EmailService(mock(TemplateRenderService.class), new ObjectMapper(),
                mock(org.thymeleaf.TemplateEngine.class), recording);

        service.enviarCorreoQRAsync("visitor@example.invalid", "TOKEN", "2026-10-12", "Visitante");

        assertThat(submitted).hasSize(1);
    }

    @Test
    void mainSourcesNeverUseTheCommonPoolForAsyncWork() throws IOException {
        Pattern commonPool = Pattern.compile("CompletableFuture\\s*\\.\\s*(runAsync|supplyAsync)\\s*\\(");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String src = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = commonPool.matcher(src);
                while (m.find()) {
                    if (!callHasExecutorArgument(src, m.end())) {
                        offenders.add(file.getFileName() + ":" + (src.substring(0, m.start()).split("\n").length));
                    }
                }
            }
        }
        assertThat(offenders).as("runAsync/supplyAsync without an explicit executor").isEmpty();
    }

    /** True when the call starting at {@code open} (just after its '(') passes a second, top-level argument. */
    private static boolean callHasExecutorArgument(String src, int open) {
        int depth = 1;
        for (int i = open; i < src.length() && depth > 0; i++) {
            char c = src.charAt(i);
            if (c == '(' || c == '{' || c == '[') {
                depth++;
            } else if (c == ')' || c == '}' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 1) {
                return true;
            }
        }
        return false;
    }
}
