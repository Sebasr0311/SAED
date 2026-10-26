package com.saed.backend.platform.onboarding;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.person.dto.ResidentRegistrationResult;
import com.saed.backend.platform.dto.EmailDispatchResponseDTO;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** DEC-13: no API response, e-mail method or template carries a password. */
class NoPasswordContractTest {

    @Test
    void emailServiceHasNoCredentialMailMethod() {
        boolean any = Arrays.stream(EmailService.class.getDeclaredMethods()).map(Method::getName)
                .anyMatch(n -> n.startsWith("enviarBienvenidaCredenciales"));
        assertFalse(any, "the onboarding welcome mail with a password was replaced by the activation link");
    }

    @Test
    void credentialsTemplateIsGone() {
        assertFalse(Files.exists(Paths.get("src", "main", "resources", "templates", "correos",
                "correo_bienvenida_credenciales.html")));
    }

    @Test
    void resendResponseCarriesNoPasswordField() {
        boolean any = Arrays.stream(EmailDispatchResponseDTO.class.getDeclaredFields())
                .anyMatch(f -> f.getName().toLowerCase().contains("password"));
        assertFalse(any);
    }

    @Test
    void residentRegistrationResultCarriesNoPasswordField() {
        boolean any = Arrays.stream(ResidentRegistrationResult.class.getRecordComponents()).map(RecordComponent::getName)
                .anyMatch(n -> n.toLowerCase().contains("password"));
        assertFalse(any);
    }
}
