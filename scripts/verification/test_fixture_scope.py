"""Pure checks of the exact foreign-fixture detector used by the isolation matrix and the pool checks."""
import importlib.util
import json
from pathlib import Path
import unittest

HERE = Path(__file__).parent


def load(name):
    spec = importlib.util.spec_from_file_location(name, HERE / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


scope = load("fixture_scope")
oracle = load("isolated_oracle")
ACCOUNTS = oracle.fixture_accounts()


class FixtureIdsTest(unittest.TestCase):
    def test_organization_ids_come_from_the_fixture_definition(self):
        org2 = scope.fixture_ids(ACCOUNTS, 92001)
        self.assertEqual({92001, 92021}, {i for i in org2 if i in (92001, 92021)})
        for unit in (92001, 92011, 92021, 92031):
            self.assertIn(unit, org2)
        for person in range(92002, 92007):
            self.assertIn(person, org2)

    def test_the_other_organization_and_sequence_ids_are_not_part_of_the_set(self):
        org2 = scope.fixture_ids(ACCOUNTS, 92001)
        self.assertNotIn(91001, org2)
        self.assertNotIn(92007, org2)
        self.assertNotIn(92500, org2)

    def test_the_mixed_role_assignment_belongs_to_organization_one(self):
        self.assertIn(93001, scope.fixture_ids(ACCOUNTS, 91001))
        self.assertNotIn(93001, scope.fixture_ids(ACCOUNTS, 92001))

    def test_every_generated_fixture_row_is_covered_by_its_own_set(self):
        for tenant in (91001, 92001):
            ids = scope.fixture_ids(ACCOUNTS, tenant)
            for account in ACCOUNTS:
                if account["organization"] == tenant:
                    self.assertIn(account["id"], ids)


class FoundIdsTest(unittest.TestCase):
    foreign = scope.fixture_ids(ACCOUNTS, 92001)

    def body(self, payload):
        return json.dumps(payload).encode()

    def test_a_sequence_id_inside_the_old_range_is_not_a_leak(self):
        payload = {"data": [{"id": 92007, "nombre": "created by a scenario in org 91001"}, {"ref": "ID 92123"}]}
        self.assertEqual([], scope.found_ids(self.body(payload), self.foreign))

    def test_a_real_foreign_fixture_id_is_a_leak(self):
        payload = {"data": [{"id": 92007}, {"idUnidad": 92011}, {"note": "property 92021 rows"}]}
        self.assertEqual([92011, 92021], scope.found_ids(self.body(payload), self.foreign))

    def test_booleans_longer_numbers_and_non_json_bodies(self):
        self.assertEqual([], scope.found_ids(self.body({"ok": True, "n": 920011, "s": "x920011y"}), self.foreign))
        self.assertEqual([92001], scope.found_ids(b"plain text mentioning 92001", self.foreign))
        self.assertEqual([], scope.found_ids(b"", self.foreign))


if __name__ == "__main__":
    unittest.main()
