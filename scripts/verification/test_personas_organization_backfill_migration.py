"""Static checks of V5.69 (back-fill of PERSONAS.ID_ORGANIZACION, DEC-01) and of the procedures' PERSONAS handling.
No database."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "database" / "migrations"
BACKFILL = next(MIGRATIONS.glob("V5.69__*.sql"), None)


def code(text: str) -> str:
    return re.sub(r"--.*", "", text)


class BackfillMigrationTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(BACKFILL, "V5.69 migration is missing")
        self.text = BACKFILL.read_text(encoding="utf-8")
        self.code = code(self.text)
        self.updates = re.findall(r"UPDATE PERSONAS p\b.*?;\n", self.code, flags=re.S)

    def test_runs_under_the_bootstrap_context_as_the_application_owner(self):
        self.assertNotIn("__sec_", BACKFILL.name)
        self.assertTrue(self.text.lstrip("- =\n").startswith("SAED 2.0 - V5.69"))
        self.assertIn("PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1)", self.code)
        self.assertIn("PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN')", self.code)

    def test_three_plain_updates_and_no_policy_incompatible_statements(self):
        self.assertEqual(3, len(self.updates))
        self.assertNotRegex(self.code, r"\bMERGE\b|\bDELETE\b|ALTER TABLE|WHEN OTHERS")

    def test_every_update_touches_only_persons_without_an_organization(self):
        for update in self.updates:
            self.assertRegex(update, r"WHERE p\.ID_ORGANIZACION IS NULL")

    def test_a_person_gets_an_organization_only_when_its_tier_points_to_exactly_one(self):
        for update in self.updates:
            self.assertRegex(update, r"COUNT\(DISTINCT \w+\.ID_ORGANIZACION\)")
            self.assertRegex(update, r"\) = 1;\n$")

    def test_tiers_follow_the_documented_order_and_a_weaker_tier_never_overrides_a_stronger_one(self):
        assignments, housing, visits = self.updates
        self.assertIn("USUARIO_ASIGNACIONES", assignments)
        self.assertNotIn("RESIDENTES_UNIDAD", assignments)
        for table in ("RESIDENTES_UNIDAD", "PROPIETARIOS_UNIDAD"):
            self.assertIn(table, housing)
        self.assertRegex(housing, r"NOT EXISTS \(SELECT 1 FROM USUARIOS u JOIN USUARIO_ASIGNACIONES")
        for table in ("VISITANTES", "VISITAS"):
            self.assertIn(table, visits)
        self.assertRegex(visits, r"NOT EXISTS \(SELECT 1 FROM RESIDENTES_UNIDAD")
        self.assertRegex(visits, r"NOT EXISTS \(SELECT 1 FROM PROPIETARIOS_UNIDAD")
        self.assertRegex(visits, r"NOT EXISTS \(SELECT 1 FROM USUARIOS u JOIN USUARIO_ASIGNACIONES")

    def test_the_organization_always_comes_from_tenant_tables_never_from_a_literal(self):
        for update in self.updates:
            self.assertNotRegex(update, r"ID_ORGANIZACION\s*=\s*\d")

    def test_the_rule_and_the_unresolved_rows_are_documented(self):
        for phrase in ("exactly ONE distinct organization", "ambiguous", "stay NULL", "Idempotent"):
            self.assertIn(phrase, self.text)


class PersonasCreationInTheSecurityProceduresTest(unittest.TestCase):
    def test_the_security_procedures_never_create_persons(self):
        # They only read PERSONAS; every person is inserted by the application with ID_ORGANIZACION (guarded in Java).
        for pattern in ("V5.66__sec_*.sql", "V5.67__sec_*.sql", "V5.68__sec_*.sql"):
            file = next(MIGRATIONS.glob(pattern))
            self.assertNotRegex(code(file.read_text(encoding="utf-8")), r"INSERT\s+INTO\s+PERSONAS", file.name)

    def test_every_person_insert_in_the_migrations_that_create_persons_lists_the_organization(self):
        for file in MIGRATIONS.glob("V5.6[6-9]__*.sql"):
            text = code(file.read_text(encoding="utf-8"))
            for match in re.finditer(r"INSERT\s+INTO\s+PERSONAS\s*\((.*?)\)", text, flags=re.S | re.I):
                self.assertIn("ID_ORGANIZACION", match.group(1).upper(), file.name)


if __name__ == "__main__":
    unittest.main()
