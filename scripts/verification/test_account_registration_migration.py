"""Static checks of the administrator account-creation procedure (V5.68, security schema).
Row-level policies on USUARIOS only admit rows already tied to the caller's organization through an assignment, so an
administrator could never insert the first row of a new account (ORA-28115). The account row is created by a
validated definer-rights procedure; the assignment is still inserted by the administrator under the policies. No
database."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
PROCEDURE = next((ROOT / "database" / "migrations").glob("V5.68__sec_*.sql"), None)


def code(text: str) -> str:
    return re.sub(r"--.*", "", text)


class AccountProcedureTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(PROCEDURE, "V5.68 __sec_ migration is missing")
        self.text = PROCEDURE.read_text(encoding="utf-8")
        self.code = code(self.text)

    def test_runs_as_the_security_principal_without_the_application_context_block(self):
        self.assertIn("__sec_", PROCEDURE.name)
        self.assertNotIn("PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT", self.text)

    def test_definer_rights_procedure_returns_the_new_user_id(self):
        self.assertRegex(self.code, r"CREATE OR REPLACE PROCEDURE SP_REGISTRAR_CUENTA\(")
        self.assertIn("AUTHID DEFINER", self.code)
        self.assertRegex(self.code, r"p_id_usuario\s+OUT\s+NUMBER")

    def test_only_an_authenticated_tenant_administrator_with_an_assignment_in_force_may_create_accounts(self):
        for part in ("SYS_CONTEXT('SAED_CTX', 'STATE')", "SYS_CONTEXT('SAED_CTX', 'ID_USUARIO')",
                     "SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION')", "'ANONYMOUS', 'CLEARING', 'BOOTSTRAP'",
                     "v_rol NOT IN ('ADMIN_PROPIEDAD', 'ADMIN_ORGANIZACION')"):
            self.assertIn(part, self.code)
        self.assertNotIn("'SUPERADMIN'", self.code.split("BEGIN", 2)[-1].split("INSERT INTO USUARIOS")[0])
        self.assertRegex(self.code, r"ua\.FECHA_INICIO\s*<=\s*TRUNC\(SYSDATE\)")
        self.assertRegex(self.code, r"ua\.FECHA_FIN IS NULL OR ua\.FECHA_FIN\s*>=\s*TRUNC\(SYSDATE\)")
        self.assertRegex(self.code, r"ua\.ID_ORGANIZACION\s*=\s*v_org")

    def test_a_property_administrator_assignment_must_be_in_the_context_property(self):
        self.assertRegex(self.code, r"v_rol\s*=\s*'ADMIN_ORGANIZACION'\s+OR\s+ua\.ID_PROPIEDAD\s*=\s*v_prop")

    def test_the_person_must_exist_and_must_not_already_have_an_account(self):
        self.assertRegex(self.code, r"FROM PERSONAS WHERE ID_PERSONA = p_id_persona")
        self.assertRegex(self.code, r"FROM USUARIOS WHERE ID_PERSONA = p_id_persona")

    def test_creates_only_the_account_row_and_returns_its_id(self):
        self.assertRegex(self.code, r"INSERT INTO USUARIOS\s*\(")
        self.assertRegex(self.code, r"RETURNING ID_USUARIO INTO p_id_usuario")
        self.assertNotRegex(self.code, r"INSERT INTO USUARIO_ASIGNACIONES")

    def test_grants_execute_to_the_same_grantees_as_the_auth_bootstrap_package(self):
        self.assertRegex(self.code, r"TABLE_NAME = 'PKG_AUTH_BOOTSTRAP' AND PRIVILEGE = 'EXECUTE'")
        self.assertIn("GRANT EXECUTE ON SP_REGISTRAR_CUENTA TO", self.code)

    def test_uses_the_database_clock_and_swallows_nothing(self):
        self.assertNotRegex(self.code, r"CURRENT_DATE|SYSTIMESTAMP|WHEN OTHERS")


if __name__ == "__main__":
    unittest.main()
