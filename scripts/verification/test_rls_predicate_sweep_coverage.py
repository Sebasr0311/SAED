"""Unit tests for the RLS coverage check of rls_predicate_sweep.py (pure function, no Docker, no database)."""
import unittest

import rls_predicate_sweep as sweep


class UnprotectedTablesTest(unittest.TestCase):
    def test_reports_tenant_tables_without_policy(self):
        tables = ["A", "B", "C", "ROLES", "TIPOS_DOCUMENTO", "PLANES", "D"]
        cols = {"A": {"ID_PROPIEDAD"}, "B": {"ID_ORGANIZACION"}, "ROLES": {"ID_ORGANIZACION"},
                "TIPOS_DOCUMENTO": {"ID_ORGANIZACION"}, "C": {"NOMBRE"}}
        fks = [("D", "A"), ("C", "NOPE")]
        self.assertEqual(["B", "D"], sweep.unprotected_tables(tables, cols, fks, protected=["A"]))

    def test_protected_and_unscoped_tables_pass(self):
        self.assertEqual([], sweep.unprotected_tables(["A", "C"], {"A": {"ID_UNIDAD"}}, [], protected=["A"]))


if __name__ == "__main__":
    unittest.main()
