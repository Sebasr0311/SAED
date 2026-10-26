"""Static checks of V5.72 (row-level policies for GASTOS_SOPORTES_HISTORIAL and TOKENS_ACTIVACION, grants for the
security principal) and V5.73 (security-schema activation-token procedures). No database."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "database" / "migrations"
POLICIES = next(MIGRATIONS.glob("V5.72__*.sql"), None)
PROCEDURES = next(MIGRATIONS.glob("V5.73__sec_*.sql"), None)


def code(text: str) -> str:
    return re.sub(r"--.*", "", text)


class RlsPoliciesTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(POLICIES, "V5.72 migration is missing")
        self.assertNotIn("__sec_", POLICIES.name)
        self.text = POLICIES.read_text(encoding="utf-8")
        self.code = code(self.text)

    def test_starts_with_the_application_context_block(self):
        self.assertIn("PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1)", self.text.split("\n/\n")[0])

    def _policy(self, table):
        match = re.search(r"ADD_GROUPED_POLICY\((?:(?!END;).)*?object_name\s*=>\s*'" + table + r"'(?:(?!END;).)*?END;",
                          self.code, re.DOTALL)
        self.assertIsNotNone(match, table + " has no policy")
        return match.group(0)

    def test_expense_support_history_is_property_scoped(self):
        policy = self._policy("GASTOS_SOPORTES_HISTORIAL")
        self.assertIn("PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD", policy)
        self.assertIn("update_check    => TRUE", policy)
        self.assertRegex(self.code, r"ALTER TABLE GASTOS_SOPORTES_HISTORIAL ADD \(ID_PROPIEDAD NUMBER\)")
        self.assertRegex(self.code, r"UPDATE GASTOS_SOPORTES_HISTORIAL")
        self.assertIn("TRG_GASTOS_SOP_HIST_PROP", self.code)

    def test_activation_tokens_are_scoped_through_their_user(self):
        policy = self._policy("TOKENS_ACTIVACION")
        self.assertIn("PKG_SAED_SECURITY_RLS.FN_FILTRO_USUARIOS", policy)
        self.assertIn("update_check    => TRUE", policy)

    def test_policy_names_fit_30_characters_and_are_checked_first(self):
        for name in re.findall(r"policy_name\s*=>\s*'([A-Z0-9_]+)'", self.code):
            self.assertLessEqual(len(name), 30, name)
        self.assertEqual(self.code.count("FROM USER_POLICIES"), 2)

    def test_security_principal_gets_only_the_token_and_password_privileges(self):
        for grant in ("GRANT SELECT, INSERT, UPDATE ON TOKENS_ACTIVACION TO",
                      "GRANT UPDATE (HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS) ON USUARIOS TO"):
            self.assertIn(grant, self.code)
        self.assertNotRegex(self.code, r"GRANT\s+(ALL|DELETE)")

    def test_event_catalog_is_documented_as_policy_free(self):
        self.assertRegex(self.text, r"(?i)EVENTOS_SISTEMA[^\n]*\n?[^\n]*global catalog")
        self.assertNotIn("'EVENTOS_SISTEMA'", self.code)

    def test_follows_writer_rules(self):
        self.assertNotRegex(self.code, r"(?i)\bMERGE\b|WHEN OTHERS THEN NULL|CURRENT_DATE|SYSTIMESTAMP")
        self.assertNotRegex(self.code, r"(?i)ADD\s*\([^;']*DEFAULT")


class ActivationProceduresTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(PROCEDURES, "V5.73 __sec_ migration is missing")
        self.text = PROCEDURES.read_text(encoding="utf-8")
        self.code = code(self.text)

    def test_runs_as_the_security_principal_without_the_application_context_block(self):
        self.assertNotIn("PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT", self.text)

    def test_four_definer_procedures(self):
        for name in ("SP_EMITIR_TOKEN_ACTIVACION", "SP_CONSULTAR_TOKEN_ACTIVACION", "SP_ACTIVAR_CUENTA",
                     "SP_REENVIAR_TOKEN_ACTIVACION"):
            self.assertRegex(self.code, r"CREATE OR REPLACE PROCEDURE " + name + r"\(")
            self.assertIn("GRANT EXECUTE ON " + name + " TO", self.code)
        self.assertEqual(self.code.count("AUTHID DEFINER"), 4)

    def test_issuance_requires_a_business_session_and_scopes_the_target(self):
        issue = self.code.split("CREATE OR REPLACE PROCEDURE SP_EMITIR_TOKEN_ACTIVACION")[1].split("END SP_EMITIR")[0]
        self.assertIn("v_state <> 'BUSINESS'", issue)
        for role in ("'SUPERADMIN'", "'ADMIN_ORGANIZACION'", "'ADMIN_PROPIEDAD'", "'RESIDENTE_CONVIVENCIA'"):
            self.assertIn(role, issue)
        self.assertRegex(issue, r"ua\.FECHA_INICIO\s*<=\s*TRUNC\(SYSDATE\)")
        self.assertIn("UPDATE TOKENS_ACTIVACION", issue)
        self.assertIn("INSERT INTO TOKENS_ACTIVACION", issue)

    def test_activation_is_atomic_single_use_and_never_reactivates_blocked_accounts(self):
        activate = self.code.split("CREATE OR REPLACE PROCEDURE SP_ACTIVAR_CUENTA")[1].split("END SP_ACTIVAR_CUENTA")[0]
        self.assertIn("FOR UPDATE", activate)
        self.assertIn("ESTADO IN ('ACTIVO', 'PENDIENTE_VERIFICACION')", activate)
        self.assertRegex(activate, r"SET\s+USADO\s*=\s*1")
        self.assertIn("p_ahora", activate)

    def test_conventions(self):
        self.assertNotRegex(self.code, r"WHEN OTHERS|CURRENT_DATE|SYSTIMESTAMP|\bMERGE\b")
        self.assertRegex(self.code, r"TABLE_NAME = 'PKG_AUTH_BOOTSTRAP' AND PRIVILEGE = 'EXECUTE'")


if __name__ == "__main__":
    unittest.main()
