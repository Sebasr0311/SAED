package com.saed.backend.person.service;

import java.util.Map;

/**
 * Creates an access account inside the caller's transaction. A refusal (invalid role, scope, duplicate user name,
 * plan limit) must be thrown as an exception so the whole registration rolls back.
 */
public interface AccountProvisioner {

    /** Returns the provisioning result (idUsuario, generated password if any, ...). */
    Map<String, Object> provision(Map<String, Object> payload);
}
