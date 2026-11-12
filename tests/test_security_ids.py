import unittest

from backend.application.security_ids import legacy_security_id, normalize_security_id
from backend.domain.errors import InvalidSecurityId
from backend.domain.security import Exchange, SecurityId


class SecurityIdTests(unittest.TestCase):
    def test_canonical_ids_round_trip(self):
        for value in ("600519.SH", "000001.SZ", "920118.BJ"):
            self.assertEqual(str(SecurityId.parse(value)), value)

    def test_legacy_ids_normalize_by_market_and_code_prefix(self):
        cases = {
            "1.600519": "600519.SH",
            "0.000001": "000001.SZ",
            "0.300750": "300750.SZ",
            "0.430047": "430047.BJ",
            "0.830799": "830799.BJ",
            "0.920118": "920118.BJ",
            "1.510300": "510300.SH",
            "0.159915": "159915.SZ",
        }
        for raw, expected in cases.items():
            self.assertEqual(normalize_security_id(raw), expected, raw)

    def test_same_code_on_two_exchanges_stays_distinct(self):
        self.assertEqual(normalize_security_id("1.000001"), "000001.SH")
        self.assertEqual(normalize_security_id("0.000001"), "000001.SZ")

    def test_lowercase_and_whitespace_are_normalized(self):
        self.assertEqual(normalize_security_id(" 600519.sh "), "600519.SH")

    def test_hk_and_us_legacy_ids_are_rejected_as_unsupported_market(self):
        for raw in ("116.00700", "105.AAPL", "106.BABA", "107.SPY"):
            with self.assertRaisesRegex(InvalidSecurityId, "暂不支持该市场"):
                normalize_security_id(raw)

    def test_malformed_ids_are_rejected(self):
        for raw in ("", "abc", "600519.XX", "600519", "2.600519", "1."):
            with self.assertRaisesRegex(InvalidSecurityId, "证券标识格式不正确"):
                normalize_security_id(raw)

    def test_legacy_security_id_inverts_normalization(self):
        for raw in ("1.600519", "0.000001", "0.920118"):
            self.assertEqual(legacy_security_id(normalize_security_id(raw)), raw)


if __name__ == "__main__":
    unittest.main()
