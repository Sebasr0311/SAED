"""Static check of the migration that replaces UIX_CONTRATOS_ACTIVO_UNIDAD. The baseline index was
(ID_UNIDAD, CASE ESTADO WHEN 'ACTIVO' THEN 1 END): Oracle skips an index entry only when ALL keys are NULL, so the
unit id kept every row indexed and a unit could hold a single non-active contract. No database."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
MIGRATION = next((ROOT / "database" / "migrations").glob("V5.63__*.sql"), None)


class ContratosActivoUniqueIndexTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(MIGRATION, "V5.63 migration is missing")
        # The DDL is built inside EXECUTE IMMEDIATE strings: undo the quoting and the concatenation.
        self.text = re.sub(r"'\s*\|\|\s*'", "", MIGRATION.read_text(encoding="utf-8")).replace("''", "'")

    def test_the_new_index_has_a_single_function_based_key_that_is_null_unless_active(self):
        self.assertRegex(self.text, r"CREATE UNIQUE INDEX UIX_CONTRATOS_ACTIVO_UNIDAD ON CONTRATOS\s*\("
                                    r"\s*\(CASE WHEN ESTADO = 'ACTIVO' THEN ID_UNIDAD END\)\s*\)")

    def test_the_old_two_key_index_is_dropped_only_when_it_still_has_two_columns(self):
        self.assertRegex(self.text, r"DROP INDEX UIX_CONTRATOS_ACTIVO_UNIDAD")
        self.assertIn("USER_IND_COLUMNS", self.text)
        self.assertRegex(self.text, r"INDEX_NAME = 'UIX_CONTRATOS_ACTIVO_UNIDAD'")

    def test_creation_is_guarded_and_refuses_existing_duplicate_active_contracts(self):
        self.assertIn("USER_INDEXES", self.text)
        self.assertRegex(self.text, r"HAVING COUNT\(\*\) > 1")
        self.assertIn("RAISE_APPLICATION_ERROR", self.text)

    def test_swallows_nothing(self):
        self.assertNotRegex(self.text.upper(), r"WHEN OTHERS")


if __name__ == "__main__":
    unittest.main()
