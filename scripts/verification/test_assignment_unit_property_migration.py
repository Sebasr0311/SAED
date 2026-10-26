"""V5.59: a unit-level assignment must carry its property.

Since V5.58 the resident/porter RLS predicates scope assignments to the active context property
(`id_propiedad = <context property>`), so a unit assignment without ID_PROPIEDAD would silently lose all access.
The migration back-fills the property from the unit and then enforces it with a validated CHECK constraint.
"""
import pathlib
import re
import unittest

MIGRATION = pathlib.Path(__file__).resolve().parents[2] / "database" / "migrations" / "V5.59__asignaciones_unidad_con_propiedad.sql"


class AssignmentUnitPropertyMigrationTest(unittest.TestCase):

    def setUp(self):
        self.assertTrue(MIGRATION.exists(), f"missing {MIGRATION.name}")
        self.sql = MIGRATION.read_text(encoding="utf-8")
        # Statements only: SQL line comments may legitimately mention forbidden keywords.
        self.upper = re.sub(r"--[^\n]*", "", self.sql).upper()

    def test_back_fill_runs_before_the_constraint(self):
        back_fill = self.upper.find("UPDATE USUARIO_ASIGNACIONES")
        constraint = self.upper.find("CK_ASIG_UNIDAD_PROPIEDAD")
        self.assertGreaterEqual(back_fill, 0, "no back-fill UPDATE")
        self.assertGreater(constraint, back_fill, "constraint must be added after the back-fill")

    def test_back_fill_takes_the_property_from_the_unit(self):
        self.assertRegex(self.upper, r"SET\s+A?\.?ID_PROPIEDAD\s*=\s*\(\s*SELECT\s+U\.ID_PROPIEDAD\s+FROM\s+UNIDADES\s+U")

    def test_constraint_requires_property_when_unit_is_set(self):
        self.assertIn("ID_UNIDAD IS NULL OR ID_PROPIEDAD IS NOT NULL", self.upper)

    def test_follows_writer_rules(self):
        self.assertNotRegex(self.upper, r"\bMERGE\b")
        self.assertNotIn("WHEN OTHERS THEN NULL", self.upper)
        self.assertNotRegex(self.upper, r"\bCURRENT_DATE\b")
        self.assertIn("SET_BOOTSTRAP_CONTEXT", self.upper)


if __name__ == "__main__":
    unittest.main()
