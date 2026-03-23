"""Comprehensive pytest tests for detect_anomalous_transactions module."""

import os
import sys

import pandas as pd
import pytest

# Ensure the parent `code/` package is importable
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from detect_anomalous_transactions import (
    analyze_customer,
    assign_overall_anomaly_level,
    detect_balance_mismatch,
    detect_repeated_high_value,
    detect_sudden_increase,
    detect_transfer_then_cashout,
    generate_report,
    group_and_sort,
    load_dataset,
)

# ---------------------------------------------------------------------------
# Helpers – reusable minimal DataFrames
# ---------------------------------------------------------------------------

COLUMNS = [
    "step",
    "type",
    "amount",
    "nameorig",
    "oldbalanceorg",
    "newbalanceorig",
    "namedest",
    "oldbalancedest",
    "newbalancedest",
    "isfraud",
    "isflaggedfraud",
]


def _make_df(rows: list[list]) -> pd.DataFrame:
    """Build a DataFrame from a list of row-lists using the standard columns."""
    return pd.DataFrame(rows, columns=COLUMNS)


def _single_txn(
    step=1,
    txn_type="PAYMENT",
    amount=100.0,
    nameorig="C001",
    oldbalanceorg=1000.0,
    newbalanceorig=900.0,
    namedest="M001",
    oldbalancedest=0.0,
    newbalancedest=100.0,
    isfraud=0,
    isflaggedfraud=0,
) -> pd.DataFrame:
    """Return a single-row DataFrame with sensible defaults."""
    return _make_df(
        [
            [
                step,
                txn_type,
                amount,
                nameorig,
                oldbalanceorg,
                newbalanceorig,
                namedest,
                oldbalancedest,
                newbalancedest,
                isfraud,
                isflaggedfraud,
            ]
        ]
    )


# ===================================================================
# Tests for load_dataset
# ===================================================================


class TestLoadDataset:
    """Tests for load_dataset()."""

    def test_basic_load(self, tmp_path):
        """Load a simple CSV and verify shape and column normalisation."""
        csv = tmp_path / "data.csv"
        csv.write_text(
            "step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,"
            "nameDest,oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud\n"
            "1,PAYMENT,100.0,C001,1000,900,M001,0,100,0,0\n"
        )
        df = load_dataset(str(csv))
        assert len(df) == 1
        # All columns should be lowercase
        for col in df.columns:
            assert col == col.lower(), f"Column '{col}' is not lowercase"
        assert "nameorig" in df.columns
        assert "oldbalanceorg" in df.columns

    def test_columns_stripped_and_lowered(self, tmp_path):
        """Columns with spaces and mixed case are normalised."""
        csv = tmp_path / "data.csv"
        csv.write_text(" Step , Type , Amount \n1,PAYMENT,50\n")
        df = load_dataset(str(csv))
        assert list(df.columns) == ["step", "type", "amount"]

    def test_multiple_rows(self, tmp_path):
        """Multiple rows are loaded correctly."""
        csv = tmp_path / "data.csv"
        lines = (
            "step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,"
            "nameDest,oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud\n"
        )
        for i in range(5):
            lines += f"{i},PAYMENT,{100 + i},C001,1000,900,M001,0,100,0,0\n"
        csv.write_text(lines)
        df = load_dataset(str(csv))
        assert len(df) == 5

    def test_empty_csv(self, tmp_path):
        """An empty CSV (headers only) results in an empty DataFrame."""
        csv = tmp_path / "data.csv"
        csv.write_text(
            "step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,"
            "nameDest,oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud\n"
        )
        df = load_dataset(str(csv))
        assert len(df) == 0
        assert "nameorig" in df.columns


# ===================================================================
# Tests for group_and_sort
# ===================================================================


class TestGroupAndSort:
    """Tests for group_and_sort()."""

    def test_sorts_by_nameorig_then_step(self):
        df = _make_df(
            [
                [3, "PAYMENT", 10, "C002", 0, 0, "M", 0, 0, 0, 0],
                [1, "PAYMENT", 20, "C001", 0, 0, "M", 0, 0, 0, 0],
                [2, "PAYMENT", 30, "C001", 0, 0, "M", 0, 0, 0, 0],
                [1, "PAYMENT", 40, "C002", 0, 0, "M", 0, 0, 0, 0],
            ]
        )
        result = group_and_sort(df)
        assert result["nameorig"].tolist() == ["C001", "C001", "C002", "C002"]
        assert result["step"].tolist() == [1, 2, 1, 3]

    def test_index_is_reset(self):
        df = _make_df(
            [
                [2, "PAYMENT", 10, "C001", 0, 0, "M", 0, 0, 0, 0],
                [1, "PAYMENT", 20, "C001", 0, 0, "M", 0, 0, 0, 0],
            ]
        )
        result = group_and_sort(df)
        assert list(result.index) == [0, 1]

    def test_single_row(self):
        df = _single_txn()
        result = group_and_sort(df)
        assert len(result) == 1

    def test_empty_dataframe(self):
        df = _make_df([])
        result = group_and_sort(df)
        assert len(result) == 0


# ===================================================================
# Tests for detect_repeated_high_value
# ===================================================================


class TestDetectRepeatedHighValue:
    """Tests for detect_repeated_high_value()."""

    def test_no_high_value(self):
        """All amounts below threshold → no anomalies."""
        df = _make_df(
            [
                [1, "PAYMENT", 50, "C001", 1000, 950, "M", 0, 50, 0, 0],
                [2, "PAYMENT", 60, "C001", 950, 890, "M", 0, 60, 0, 0],
            ]
        )
        result = detect_repeated_high_value(df, high_value_threshold=100.0)
        assert result == []

    def test_single_high_value_below_min_sequence(self):
        """Only 1 high-value txn (< MIN_SEQUENCE_LENGTH=2) → no anomaly."""
        df = _make_df(
            [
                [1, "PAYMENT", 200, "C001", 1000, 800, "M", 0, 200, 0, 0],
                [2, "PAYMENT", 50, "C001", 800, 750, "M", 0, 50, 0, 0],
            ]
        )
        result = detect_repeated_high_value(df, high_value_threshold=100.0)
        assert result == []

    def test_two_high_value_medium_level(self):
        """Exactly 2 high-value txns → MEDIUM anomaly."""
        df = _make_df(
            [
                [1, "PAYMENT", 200, "C001", 1000, 800, "M", 0, 200, 0, 0],
                [2, "TRANSFER", 300, "C001", 800, 500, "M", 0, 300, 0, 0],
            ]
        )
        result = detect_repeated_high_value(df, high_value_threshold=100.0)
        assert len(result) == 1
        assert result[0]["anomaly_level"] == "MEDIUM"
        assert "Repeated high-value" in result[0]["pattern"]
        assert "PAYMENT -> TRANSFER" in result[0]["pattern"]

    def test_three_high_value_high_level(self):
        """3 or more high-value txns → HIGH anomaly."""
        df = _make_df(
            [
                [1, "PAYMENT", 200, "C001", 1000, 800, "M", 0, 0, 0, 0],
                [2, "TRANSFER", 300, "C001", 800, 500, "M", 0, 0, 0, 0],
                [3, "CASH_OUT", 150, "C001", 500, 350, "M", 0, 0, 0, 0],
            ]
        )
        result = detect_repeated_high_value(df, high_value_threshold=100.0)
        assert len(result) == 1
        assert result[0]["anomaly_level"] == "HIGH"

    def test_threshold_boundary(self):
        """Amount exactly equal to threshold counts as high-value."""
        df = _make_df(
            [
                [1, "PAYMENT", 100, "C001", 1000, 900, "M", 0, 0, 0, 0],
                [2, "PAYMENT", 100, "C001", 900, 800, "M", 0, 0, 0, 0],
            ]
        )
        result = detect_repeated_high_value(df, high_value_threshold=100.0)
        assert len(result) == 1
        assert result[0]["anomaly_level"] == "MEDIUM"

    def test_empty_dataframe(self):
        df = _make_df([])
        result = detect_repeated_high_value(df, high_value_threshold=100.0)
        assert result == []

    def test_explanation_contains_amounts(self):
        """The explanation string should reference the transaction amounts."""
        df = _make_df(
            [
                [1, "PAYMENT", 200, "C001", 1000, 800, "M", 0, 0, 0, 0],
                [2, "PAYMENT", 300, "C001", 800, 500, "M", 0, 0, 0, 0],
            ]
        )
        result = detect_repeated_high_value(df, high_value_threshold=100.0)
        assert "200" in result[0]["explanation"]
        assert "300" in result[0]["explanation"]


# ===================================================================
# Tests for detect_transfer_then_cashout
# ===================================================================


class TestDetectTransferThenCashout:
    """Tests for detect_transfer_then_cashout()."""

    def test_transfer_then_cashout(self):
        """TRANSFER immediately followed by CASH_OUT → CRITICAL."""
        df = _make_df(
            [
                [1, "TRANSFER", 5000, "C001", 10000, 5000, "C002", 0, 5000, 0, 0],
                [2, "CASH_OUT", 5000, "C001", 5000, 0, "C003", 0, 5000, 0, 0],
            ]
        )
        result = detect_transfer_then_cashout(df)
        assert len(result) == 1
        assert result[0]["anomaly_level"] == "CRITICAL"
        assert result[0]["pattern"] == "TRANSFER -> CASH_OUT"
        assert "5000.00" in result[0]["explanation"]

    def test_no_pattern(self):
        """PAYMENT followed by DEBIT → no anomaly."""
        df = _make_df(
            [
                [1, "PAYMENT", 100, "C001", 1000, 900, "M", 0, 100, 0, 0],
                [2, "DEBIT", 200, "C001", 900, 700, "M", 0, 200, 0, 0],
            ]
        )
        result = detect_transfer_then_cashout(df)
        assert result == []

    def test_cashout_then_transfer_no_match(self):
        """Reversed order (CASH_OUT then TRANSFER) → no anomaly."""
        df = _make_df(
            [
                [1, "CASH_OUT", 5000, "C001", 10000, 5000, "C002", 0, 5000, 0, 0],
                [2, "TRANSFER", 5000, "C001", 5000, 0, "C003", 0, 5000, 0, 0],
            ]
        )
        result = detect_transfer_then_cashout(df)
        assert result == []

    def test_transfer_not_immediately_followed_by_cashout(self):
        """TRANSFER then PAYMENT then CASH_OUT → no anomaly (not immediate)."""
        df = _make_df(
            [
                [1, "TRANSFER", 5000, "C001", 10000, 5000, "C002", 0, 5000, 0, 0],
                [2, "PAYMENT", 100, "C001", 5000, 4900, "M", 0, 100, 0, 0],
                [3, "CASH_OUT", 4900, "C001", 4900, 0, "C003", 0, 4900, 0, 0],
            ]
        )
        result = detect_transfer_then_cashout(df)
        assert result == []

    def test_multiple_patterns(self):
        """Two TRANSFER→CASH_OUT pairs → two anomalies."""
        df = _make_df(
            [
                [1, "TRANSFER", 1000, "C001", 10000, 9000, "C002", 0, 1000, 0, 0],
                [2, "CASH_OUT", 1000, "C001", 9000, 8000, "C003", 0, 1000, 0, 0],
                [3, "TRANSFER", 2000, "C001", 8000, 6000, "C004", 0, 2000, 0, 0],
                [4, "CASH_OUT", 2000, "C001", 6000, 4000, "C005", 0, 2000, 0, 0],
            ]
        )
        result = detect_transfer_then_cashout(df)
        assert len(result) == 2

    def test_single_transaction(self):
        """Single txn → no anomaly (need at least 2)."""
        df = _single_txn(txn_type="TRANSFER")
        result = detect_transfer_then_cashout(df)
        assert result == []

    def test_empty_dataframe(self):
        df = _make_df([])
        result = detect_transfer_then_cashout(df)
        assert result == []


# ===================================================================
# Tests for detect_sudden_increase
# ===================================================================


class TestDetectSuddenIncrease:
    """Tests for detect_sudden_increase()."""

    def test_spike_detected(self):
        """Amount 5x the running average triggers anomaly."""
        df = _make_df(
            [
                [1, "PAYMENT", 100, "C001", 10000, 9900, "M", 0, 0, 0, 0],
                [2, "PAYMENT", 600, "C001", 9900, 9300, "M", 0, 0, 0, 0],
            ]
        )
        # running_avg before txn 2 = 100; 600/100 = 6x >= 5x → anomaly
        result = detect_sudden_increase(df)
        assert len(result) == 1
        assert result[0]["anomaly_level"] == "HIGH"
        assert "Sudden increase" in result[0]["pattern"]

    def test_no_spike(self):
        """Gradual increase → no anomaly."""
        df = _make_df(
            [
                [1, "PAYMENT", 100, "C001", 10000, 9900, "M", 0, 0, 0, 0],
                [2, "PAYMENT", 200, "C001", 9900, 9700, "M", 0, 0, 0, 0],
                [3, "PAYMENT", 300, "C001", 9700, 9400, "M", 0, 0, 0, 0],
            ]
        )
        result = detect_sudden_increase(df)
        assert result == []

    def test_exactly_5x_triggers(self):
        """Amount exactly 5x the running avg should trigger."""
        df = _make_df(
            [
                [1, "PAYMENT", 100, "C001", 10000, 9900, "M", 0, 0, 0, 0],
                [2, "PAYMENT", 500, "C001", 9900, 9400, "M", 0, 0, 0, 0],
            ]
        )
        result = detect_sudden_increase(df)
        assert len(result) == 1

    def test_single_transaction_no_anomaly(self):
        """Single txn → fewer than MIN_SEQUENCE_LENGTH → no anomaly."""
        df = _single_txn(amount=99999)
        result = detect_sudden_increase(df)
        assert result == []

    def test_empty_dataframe(self):
        df = _make_df([])
        result = detect_sudden_increase(df)
        assert result == []

    def test_multiple_spikes(self):
        """Multiple spikes in a sequence."""
        df = _make_df(
            [
                [1, "PAYMENT", 10, "C001", 10000, 9990, "M", 0, 0, 0, 0],
                [2, "PAYMENT", 100, "C001", 9990, 9890, "M", 0, 0, 0, 0],
                # running_avg before txn 3 = (10+100)/2 = 55; 300/55 ≈ 5.45 → spike
                [3, "PAYMENT", 300, "C001", 9890, 9590, "M", 0, 0, 0, 0],
            ]
        )
        result = detect_sudden_increase(df)
        # txn2: 100/10 = 10x → spike; txn3: 300/55 ≈ 5.45x → spike
        assert len(result) == 2

    def test_explanation_contains_factor(self):
        """The explanation should contain the multiplier."""
        df = _make_df(
            [
                [1, "PAYMENT", 100, "C001", 10000, 9900, "M", 0, 0, 0, 0],
                [2, "PAYMENT", 600, "C001", 9900, 9300, "M", 0, 0, 0, 0],
            ]
        )
        result = detect_sudden_increase(df)
        assert "6.0x" in result[0]["explanation"]


# ===================================================================
# Tests for detect_balance_mismatch
# ===================================================================


class TestDetectBalanceMismatch:
    """Tests for detect_balance_mismatch()."""

    def test_account_drained_transfer(self):
        """TRANSFER draining account from >0 to 0 → CRITICAL."""
        df = _make_df(
            [
                [1, "TRANSFER", 5000, "C001", 5000, 0, "C002", 0, 5000, 0, 0],
            ]
        )
        result = detect_balance_mismatch(df)
        assert len(result) == 1
        assert result[0]["anomaly_level"] == "CRITICAL"
        assert "Account drained via TRANSFER" in result[0]["pattern"]

    def test_account_drained_cashout(self):
        """CASH_OUT draining account → CRITICAL."""
        df = _make_df(
            [
                [1, "CASH_OUT", 3000, "C001", 3000, 0, "C002", 0, 3000, 0, 0],
            ]
        )
        result = detect_balance_mismatch(df)
        assert len(result) == 1
        assert "CASH_OUT" in result[0]["pattern"]

    def test_no_mismatch_balance_remains(self):
        """Balance does not go to 0 → no anomaly."""
        df = _make_df(
            [
                [1, "TRANSFER", 1000, "C001", 5000, 4000, "C002", 0, 1000, 0, 0],
            ]
        )
        result = detect_balance_mismatch(df)
        assert result == []

    def test_no_mismatch_payment_type(self):
        """PAYMENT type draining to 0 → no anomaly (only TRANSFER/CASH_OUT trigger)."""
        df = _make_df(
            [
                [1, "PAYMENT", 5000, "C001", 5000, 0, "M001", 0, 5000, 0, 0],
            ]
        )
        result = detect_balance_mismatch(df)
        assert result == []

    def test_oldbalance_zero(self):
        """oldbalanceorg == 0 → does not trigger (condition requires >0)."""
        df = _make_df(
            [
                [1, "TRANSFER", 0, "C001", 0, 0, "C002", 0, 0, 0, 0],
            ]
        )
        result = detect_balance_mismatch(df)
        assert result == []

    def test_multiple_drain_rows(self):
        """Multiple draining transactions → multiple anomalies."""
        df = _make_df(
            [
                [1, "TRANSFER", 5000, "C001", 5000, 0, "C002", 0, 5000, 0, 0],
                [2, "CASH_OUT", 3000, "C001", 3000, 0, "C003", 0, 3000, 0, 0],
            ]
        )
        result = detect_balance_mismatch(df)
        assert len(result) == 2

    def test_empty_dataframe(self):
        df = _make_df([])
        result = detect_balance_mismatch(df)
        assert result == []

    def test_explanation_contains_amounts(self):
        df = _make_df(
            [
                [1, "TRANSFER", 5000, "C001", 5000, 0, "C002", 0, 5000, 0, 0],
            ]
        )
        result = detect_balance_mismatch(df)
        assert "5000.00" in result[0]["explanation"]
        assert "0.00" in result[0]["explanation"]


# ===================================================================
# Tests for assign_overall_anomaly_level
# ===================================================================


class TestAssignOverallAnomalyLevel:
    """Tests for assign_overall_anomaly_level()."""

    def test_empty_list_returns_none(self):
        assert assign_overall_anomaly_level([]) == "NONE"

    def test_single_critical(self):
        anomalies = [{"anomaly_level": "CRITICAL", "pattern": "x", "explanation": "y"}]
        assert assign_overall_anomaly_level(anomalies) == "CRITICAL"

    def test_single_medium(self):
        anomalies = [{"anomaly_level": "MEDIUM", "pattern": "x", "explanation": "y"}]
        assert assign_overall_anomaly_level(anomalies) == "MEDIUM"

    def test_mixed_returns_highest(self):
        anomalies = [
            {"anomaly_level": "LOW", "pattern": "x", "explanation": "y"},
            {"anomaly_level": "HIGH", "pattern": "x", "explanation": "y"},
            {"anomaly_level": "MEDIUM", "pattern": "x", "explanation": "y"},
        ]
        assert assign_overall_anomaly_level(anomalies) == "HIGH"

    def test_critical_beats_high(self):
        anomalies = [
            {"anomaly_level": "HIGH", "pattern": "x", "explanation": "y"},
            {"anomaly_level": "CRITICAL", "pattern": "x", "explanation": "y"},
        ]
        assert assign_overall_anomaly_level(anomalies) == "CRITICAL"

    def test_all_same_level(self):
        anomalies = [
            {"anomaly_level": "MEDIUM", "pattern": "x", "explanation": "y"},
            {"anomaly_level": "MEDIUM", "pattern": "x", "explanation": "y"},
        ]
        assert assign_overall_anomaly_level(anomalies) == "MEDIUM"

    def test_low_only(self):
        anomalies = [{"anomaly_level": "LOW", "pattern": "x", "explanation": "y"}]
        assert assign_overall_anomaly_level(anomalies) == "LOW"


# ===================================================================
# Tests for analyze_customer
# ===================================================================


class TestAnalyzeCustomer:
    """Tests for analyze_customer()."""

    def test_no_anomalies(self):
        """Normal transactions with no anomalies."""
        df = _make_df(
            [
                [1, "PAYMENT", 50, "C001", 1000, 950, "M", 0, 50, 0, 0],
                [2, "PAYMENT", 60, "C001", 950, 890, "M", 0, 60, 0, 0],
            ]
        )
        result = analyze_customer("C001", df, high_value_threshold=1000.0)
        assert result == []

    def test_detects_transfer_cashout(self):
        """Should detect TRANSFER→CASH_OUT pattern."""
        df = _make_df(
            [
                [1, "TRANSFER", 5000, "C001", 10000, 5000, "C002", 0, 5000, 0, 0],
                [2, "CASH_OUT", 5000, "C001", 5000, 0, "C003", 0, 5000, 0, 0],
            ]
        )
        result = analyze_customer("C001", df, high_value_threshold=100000.0)
        patterns = [a["pattern"] for a in result]
        assert "TRANSFER -> CASH_OUT" in patterns

    def test_detects_balance_mismatch(self):
        """Should detect account drain."""
        df = _make_df(
            [
                [1, "TRANSFER", 5000, "C001", 5000, 0, "C002", 0, 5000, 0, 0],
            ]
        )
        result = analyze_customer("C001", df, high_value_threshold=100000.0)
        patterns = [a["pattern"] for a in result]
        assert any("Account drained" in p for p in patterns)

    def test_detects_repeated_high_value(self):
        """Should detect repeated high-value transactions."""
        df = _make_df(
            [
                [1, "PAYMENT", 5000, "C001", 10000, 5000, "M", 0, 0, 0, 0],
                [2, "PAYMENT", 6000, "C001", 5000, 0, "M", 0, 0, 0, 0],
            ]
        )
        result = analyze_customer("C001", df, high_value_threshold=1000.0)
        patterns = [a["pattern"] for a in result]
        assert any("Repeated high-value" in p for p in patterns)

    def test_detects_sudden_increase(self):
        """Should detect sudden increase."""
        df = _make_df(
            [
                [1, "PAYMENT", 100, "C001", 10000, 9900, "M", 0, 0, 0, 0],
                [2, "PAYMENT", 600, "C001", 9900, 9300, "M", 0, 0, 0, 0],
            ]
        )
        result = analyze_customer("C001", df, high_value_threshold=100000.0)
        patterns = [a["pattern"] for a in result]
        assert any("Sudden increase" in p for p in patterns)

    def test_multiple_detectors_fire(self):
        """Multiple detectors can fire simultaneously."""
        df = _make_df(
            [
                [1, "TRANSFER", 5000, "C001", 10000, 5000, "C002", 0, 5000, 0, 0],
                [2, "CASH_OUT", 5000, "C001", 5000, 0, "C003", 0, 5000, 0, 0],
            ]
        )
        # threshold = 1000 → both are high-value; TRANSFER→CASH_OUT; balance drain
        result = analyze_customer("C001", df, high_value_threshold=1000.0)
        assert len(result) >= 2  # at least transfer→cashout + balance mismatch

    def test_empty_dataframe(self):
        df = _make_df([])
        result = analyze_customer("C001", df, high_value_threshold=1000.0)
        assert result == []


# ===================================================================
# Tests for generate_report
# ===================================================================


class TestGenerateReport:
    """Tests for generate_report()."""

    def test_sorts_by_severity(self):
        """Report rows should be sorted CRITICAL → HIGH → MEDIUM → LOW → NONE."""
        results = [
            {
                "customer_id": "C001",
                "sequence_pattern": "Normal",
                "anomaly_level": "NONE",
                "overall_anomaly_level": "NONE",
                "explanation": "No anomalies.",
            },
            {
                "customer_id": "C002",
                "sequence_pattern": "Repeated high-value",
                "anomaly_level": "HIGH",
                "overall_anomaly_level": "HIGH",
                "explanation": "High value detected.",
            },
            {
                "customer_id": "C003",
                "sequence_pattern": "TRANSFER -> CASH_OUT",
                "anomaly_level": "CRITICAL",
                "overall_anomaly_level": "CRITICAL",
                "explanation": "Money laundering pattern.",
            },
            {
                "customer_id": "C004",
                "sequence_pattern": "Repeated high-value",
                "anomaly_level": "MEDIUM",
                "overall_anomaly_level": "MEDIUM",
                "explanation": "Medium level.",
            },
        ]
        report = generate_report(results)
        levels = report["overall_anomaly_level"].tolist()
        assert levels == ["CRITICAL", "HIGH", "MEDIUM", "NONE"]

    def test_no_sort_column_in_output(self):
        """The internal _sort column should not appear in the output."""
        results = [
            {
                "customer_id": "C001",
                "sequence_pattern": "Normal",
                "anomaly_level": "NONE",
                "overall_anomaly_level": "NONE",
                "explanation": "No anomalies.",
            },
        ]
        report = generate_report(results)
        assert "_sort" not in report.columns

    def test_index_is_reset(self):
        results = [
            {
                "customer_id": "C001",
                "sequence_pattern": "Normal",
                "anomaly_level": "NONE",
                "overall_anomaly_level": "NONE",
                "explanation": "No anomalies.",
            },
            {
                "customer_id": "C002",
                "sequence_pattern": "Drain",
                "anomaly_level": "CRITICAL",
                "overall_anomaly_level": "CRITICAL",
                "explanation": "Drain.",
            },
        ]
        report = generate_report(results)
        assert list(report.index) == [0, 1]

    def test_single_result(self):
        results = [
            {
                "customer_id": "C001",
                "sequence_pattern": "Normal",
                "anomaly_level": "NONE",
                "overall_anomaly_level": "NONE",
                "explanation": "No anomalies.",
            },
        ]
        report = generate_report(results)
        assert len(report) == 1

    def test_all_columns_present(self):
        results = [
            {
                "customer_id": "C001",
                "sequence_pattern": "Normal",
                "anomaly_level": "NONE",
                "overall_anomaly_level": "NONE",
                "explanation": "No anomalies.",
            },
        ]
        report = generate_report(results)
        expected_cols = {
            "customer_id",
            "sequence_pattern",
            "anomaly_level",
            "overall_anomaly_level",
            "explanation",
        }
        assert expected_cols.issubset(set(report.columns))
