package com.saed.backend.platform.exception;

/**
 * Exception thrown when a tenant attempts to access a feature or endpoint
 * corresponding to a module not entitled under the active membership plan.
 */
public class ModuleNotEntitledException extends RuntimeException {

    private final String moduleCode;

    public ModuleNotEntitledException(String moduleCode) {
        super("El módulo '" + moduleCode + "' no está incluido en el plan contratado por la copropiedad.");
        this.moduleCode = moduleCode;
    }

    public ModuleNotEntitledException(String moduleCode, String message) {
        super(message);
        this.moduleCode = moduleCode;
    }

    public String getModuleCode() {
        return moduleCode;
    }
}
