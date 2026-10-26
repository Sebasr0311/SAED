"""Static checks of the security-schema migration that tightens SP_REVOCAR_ACCESO_UNIDAD (V5.55). No database."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
MIGRATION = next((ROOT / "database" / "migrations").glob("V5.55__sec_household_revocation*.sql"))


class HouseholdRevocationMigrationTest(unittest.TestCase):
    def setUp(self):
        self.text = MIGRATION.read_text(encoding="utf-8")

    def test_runs_as_the_security_principal_without_the_application_context_block(self):
        self.assertIn("__sec_", MIGRATION.name)
        self.assertNotIn("PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT", self.text)

    def test_the_titular_assignment_must_be_in_force_today(self):
        self.assertRegex(self.text, r"ua\.FECHA_INICIO\s*<=\s*TRUNC\(SYSDATE\)")
        self.assertRegex(self.text, r"ua\.FECHA_FIN IS NULL OR ua\.FECHA_FIN\s*>=\s*TRUNC\(SYSDATE\)")

    def test_the_caller_must_reside_as_titular_on_the_unit(self):
        self.assertRegex(self.text, r"ru\.TIPO_RESIDENTE IN \('PROPIETARIO', 'ARRENDATARIO'\)")
        self.assertIn("ru.ID_PERSONA IN (SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = v_usr)", self.text)

    def test_uses_the_database_clock_and_swallows_nothing(self):
        self.assertNotRegex(self.text, r"CURRENT_DATE|SYSTIMESTAMP|WHEN OTHERS")
        self.assertEqual(len(re.findall(r"^/\s*$", self.text, re.MULTILINE)), 1)


if __name__ == "__main__":
    unittest.main()
