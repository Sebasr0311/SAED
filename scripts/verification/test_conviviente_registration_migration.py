"""Static checks of the cohabitant registration procedure (V5.65 grants, V5.66 security-schema procedure).
A resident cannot insert USUARIOS / USUARIO_ASIGNACIONES rows (policies only admit its own user id), and USUARIOS
visibility must not be widened, so the registration runs in a validated definer-rights procedure. No database."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "database" / "migrations"
GRANTS = next(MIGRATIONS.glob("V5.65__*.sql"), None)
PROCEDURE = next(MIGRATIONS.glob("V5.66__sec_*.sql"), None)


def code(text: str) -> str:
    return re.sub(r"--.*", "", text)


class CohabitantGrantsTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(GRANTS, "V5.65 migration is missing")
        self.text = GRANTS.read_text(encoding="utf-8")

    def test_runs_as_the_application_owner_with_the_context_block(self):
        self.assertNotIn("__sec_", GRANTS.name)
        self.assertIn("PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);", self.text)

    def test_grants_only_what_the_procedure_needs_to_the_security_account(self):
        text = code(self.text)
        self.assertIn("SAED_SEC_MASTER", text)
        for grant in ("GRANT INSERT ON USUARIOS", "GRANT INSERT ON USUARIO_ASIGNACIONES",
                      "GRANT SELECT ON UNIDADES", "GRANT SELECT ON PROPIEDAD_CONFIGURACION"):
            self.assertIn(grant, text)
        self.assertNotRegex(text, r"GRANT (ALL|DELETE)")


class CohabitantProcedureTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(PROCEDURE, "V5.66 __sec_ migration is missing")
        self.text = PROCEDURE.read_text(encoding="utf-8")
        self.code = code(self.text)

    def test_runs_as_the_security_principal_without_the_application_context_block(self):
        self.assertIn("__sec_", PROCEDURE.name)
        self.assertNotIn("PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT", self.text)

    def test_definer_rights_procedure_returns_the_new_user_id(self):
        self.assertRegex(self.code, r"CREATE OR REPLACE PROCEDURE SP_REGISTRAR_CONVIVIENTE\(")
        self.assertIn("AUTHID DEFINER", self.code)
        self.assertRegex(self.code, r"p_id_usuario\s+OUT\s+NUMBER")

    def test_validates_the_caller_from_the_session_context(self):
        for part in ("SYS_CONTEXT('SAED_CTX', 'STATE')", "SYS_CONTEXT('SAED_CTX', 'ID_USUARIO')",
                     "SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD')", "'ANONYMOUS', 'CLEARING', 'BOOTSTRAP'",
                     "v_rol NOT IN ('RESIDENTE', 'PROPIETARIO')"):
            self.assertIn(part, self.code)

    def test_the_caller_must_be_an_in_force_titular_resident_of_the_unit_in_the_context_property(self):
        self.assertRegex(self.code, r"ua\.FECHA_INICIO\s*<=\s*TRUNC\(SYSDATE\)")
        self.assertRegex(self.code, r"ua\.FECHA_FIN IS NULL OR ua\.FECHA_FIN\s*>=\s*TRUNC\(SYSDATE\)")
        self.assertRegex(self.code, r"ru\.TIPO_RESIDENTE IN \('PROPIETARIO', 'ARRENDATARIO'\)")
        self.assertIn("ru.ID_PERSONA IN (SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = v_usr)", self.code)
        self.assertRegex(self.code, r"ID_PROPIEDAD\s*=\s*v_prop")

    def test_a_household_member_cannot_register_but_a_titular_assignment_without_residence_row_can(self):
        # The titular assignment is the authority; only an ACTIVE non-titular residence row of the caller denies.
        self.assertRegex(self.code, r"ru\.TIPO_RESIDENTE IN \('FAMILIAR', 'CONVIVIENTE', 'OTRO'\)")
        self.assertRegex(self.code, r"v_titular\s*=\s*0 AND v_member\s*>\s*0")
        self.assertNotRegex(self.code, r"IF v_count = 0 THEN\s+RAISE_APPLICATION_ERROR\(-20474")

    def test_enforces_the_cohabitant_quota_under_a_unit_lock(self):
        self.assertIn("LIMITE_CONVIVIENTES_POR_UNIDAD", self.code)
        self.assertRegex(self.code, r"FROM UNIDADES[^;]*FOR UPDATE")
        self.assertRegex(self.code, r"TIPO_RESIDENTE\s*=\s*'CONVIVIENTE'")
        self.assertIn("VALIDATE_CONVERSION", self.code)

    def test_creates_the_user_and_the_cohabitant_assignment_and_returns_the_id(self):
        self.assertRegex(self.code, r"INSERT INTO USUARIOS\s*\(")
        self.assertRegex(self.code, r"RETURNING ID_USUARIO INTO p_id_usuario")
        self.assertRegex(self.code, r"INSERT INTO USUARIO_ASIGNACIONES\s*\(")
        self.assertIn("'RESIDENTE_CONVIVENCIA'", self.code)

    def test_grants_execute_to_the_same_grantees_as_the_auth_bootstrap_package(self):
        self.assertRegex(self.code, r"TABLE_NAME = 'PKG_AUTH_BOOTSTRAP' AND PRIVILEGE = 'EXECUTE'")
        self.assertIn("GRANT EXECUTE ON SP_REGISTRAR_CONVIVIENTE TO", self.code)

    def test_uses_the_database_clock_and_swallows_nothing(self):
        self.assertNotRegex(self.code, r"CURRENT_DATE|SYSTIMESTAMP|WHEN OTHERS")


if __name__ == "__main__":
    unittest.main()
