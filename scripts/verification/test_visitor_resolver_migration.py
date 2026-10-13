"""Static checks of the visitor identity resolver (V5.65 grants, V5.67 security-schema procedure). No database."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "database" / "migrations"
GRANTS = next(MIGRATIONS.glob("V5.65__*.sql"), None)
PROCEDURE = next(MIGRATIONS.glob("V5.67__sec_*.sql"), None)


def code(text: str) -> str:
    return re.sub(r"--.*", "", text)


class VisitorResolverGrantsTest(unittest.TestCase):
    def test_the_security_account_may_read_and_write_only_what_the_resolver_needs(self):
        text = code(GRANTS.read_text(encoding="utf-8"))
        for grant in ("GRANT SELECT ON VISITANTES", "GRANT INSERT ON VISITANTES",
                      "GRANT UPDATE (ID_PROPIEDAD, REGISTRADO_POR) ON VISITANTES", "GRANT SELECT ON PERSONAS"):
            self.assertIn(grant, text)
        self.assertNotRegex(text, r"GRANT (ALL|DELETE)")


class VisitorResolverProcedureTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(PROCEDURE, "V5.67 __sec_ migration is missing")
        self.raw = PROCEDURE.read_text(encoding="utf-8")
        self.code = code(self.raw)

    def test_runs_as_the_security_principal_without_the_application_context_block(self):
        self.assertNotIn("PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT", self.raw)

    def test_definer_procedure_returns_only_the_visitor_id(self):
        self.assertRegex(self.code, r"CREATE OR REPLACE PROCEDURE SP_RESOLVER_VISITANTE\(")
        self.assertIn("AUTHID DEFINER", self.code)
        self.assertRegex(self.code, r"p_id_visitante_out\s+OUT\s+NUMBER")

    def test_validates_a_tenant_session_with_a_property_and_refuses_the_platform_administrator(self):
        for part in ("SYS_CONTEXT('SAED_CTX', 'STATE')", "'ANONYMOUS', 'CLEARING', 'BOOTSTRAP'",
                     "SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD')", "v_rol NOT IN ("):
            self.assertIn(part, self.code)
        self.assertNotIn("'SUPERADMIN'", re.search(r"v_rol NOT IN \([^)]*\)", self.code).group(0))

    def test_creates_the_visitor_in_the_context_property_and_adopts_only_unclaimed_rows(self):
        self.assertRegex(self.code, r"INSERT INTO VISITANTES\s*\([^)]*ID_PROPIEDAD[^)]*REGISTRADO_POR")
        self.assertRegex(self.code, r"RETURNING ID_VISITANTE INTO")
        self.assertRegex(self.code, r"UPDATE VISITANTES\s+SET ID_PROPIEDAD = v_prop, REGISTRADO_POR = v_usr")
        self.assertRegex(self.code, r"AND ID_PROPIEDAD IS NULL")

    def test_a_visitor_of_another_property_is_never_returned_by_id(self):
        self.assertRegex(self.code, r"IF v_prop_vis IS NOT NULL THEN\s+RETURN;")

    def test_grants_execute_like_the_other_security_procedures_and_swallows_nothing(self):
        self.assertRegex(self.code, r"TABLE_NAME = 'PKG_AUTH_BOOTSTRAP' AND PRIVILEGE = 'EXECUTE'")
        self.assertIn("GRANT EXECUTE ON SP_RESOLVER_VISITANTE TO", self.code)
        self.assertNotRegex(self.code, r"WHEN OTHERS|CURRENT_DATE|SYSTIMESTAMP")


if __name__ == "__main__":
    unittest.main()
