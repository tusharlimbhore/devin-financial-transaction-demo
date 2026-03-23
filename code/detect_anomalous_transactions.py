"""
Detect Anomalous Transaction Sequences

This script analyzes financial transaction data to detect anomalous
sequences for each customer. It identifies suspicious patterns such as:
- Repeated high-value transactions
- Transfer followed by cash-out
- Sudden increase in transaction amounts

Output: A sequence anomaly report with customer_id, sequence pattern,
anomaly level, and explanation.
"""

import os
import pandas as pd

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
HIGH_VALUE_THRESHOLD_PERCENTILE = 90  # transactions above this percentile are "high value"
SUDDEN_INCREASE_FACTOR = 5  # amount jump >= 5x previous avg is suspicious
MIN_SEQUENCE_LENGTH = 2  # minimum transactions to form a sequence


def load_dataset(path: str) -> pd.DataFrame:
    """Load the CSV dataset and return a DataFrame."""
    df = pd.read_csv(path)
    # Normalise column names to lowercase for consistency
    df.columns = [c.strip().lower() for c in df.columns]
    return df


def group_and_sort(df: pd.DataFrame) -> pd.DataFrame:
    """Group transactions by customer (nameorig) and sort by step (time)."""
    df = df.sort_values(by=["nameorig", "step"]).reset_index(drop=True)
    return df


def detect_repeated_high_value(
    customer_txns: pd.DataFrame, high_value_threshold: float
) -> list[dict]:
    """Detect repeated high-value transactions for a single customer."""
    anomalies: list[dict] = []
    high_vals = customer_txns[customer_txns["amount"] >= high_value_threshold]

    if len(high_vals) >= MIN_SEQUENCE_LENGTH:
        types_seq = " -> ".join(high_vals["type"].tolist())
        amounts = high_vals["amount"].tolist()
        anomalies.append(
            {
                "pattern": f"Repeated high-value: {types_seq}",
                "anomaly_level": "HIGH" if len(high_vals) >= 3 else "MEDIUM",
                "explanation": (
                    f"Customer made {len(high_vals)} transactions at or above "
                    f"the high-value threshold ({high_value_threshold:.2f}). "
                    f"Amounts: {amounts}"
                ),
            }
        )
    return anomalies


def detect_transfer_then_cashout(customer_txns: pd.DataFrame) -> list[dict]:
    """Detect TRANSFER followed immediately by CASH_OUT."""
    anomalies: list[dict] = []
    types_list = customer_txns["type"].tolist()
    amounts_list = customer_txns["amount"].tolist()

    for i in range(len(types_list) - 1):
        if types_list[i] == "TRANSFER" and types_list[i + 1] == "CASH_OUT":
            anomalies.append(
                {
                    "pattern": "TRANSFER -> CASH_OUT",
                    "anomaly_level": "CRITICAL",
                    "explanation": (
                        f"A TRANSFER of {amounts_list[i]:.2f} was immediately "
                        f"followed by a CASH_OUT of {amounts_list[i + 1]:.2f}. "
                        "This is a common money-laundering pattern."
                    ),
                }
            )
    return anomalies


def detect_sudden_increase(customer_txns: pd.DataFrame) -> list[dict]:
    """Detect a sudden spike in transaction amount relative to prior average."""
    anomalies: list[dict] = []
    amounts = customer_txns["amount"].tolist()
    types_list = customer_txns["type"].tolist()

    if len(amounts) < MIN_SEQUENCE_LENGTH:
        return anomalies

    running_sum = amounts[0]
    for i in range(1, len(amounts)):
        running_avg = running_sum / i
        if running_avg > 0 and amounts[i] / running_avg >= SUDDEN_INCREASE_FACTOR:
            anomalies.append(
                {
                    "pattern": f"Sudden increase at transaction {i + 1}",
                    "anomaly_level": "HIGH",
                    "explanation": (
                        f"Transaction amount {amounts[i]:.2f} ({types_list[i]}) is "
                        f"{amounts[i] / running_avg:.1f}x the prior average of "
                        f"{running_avg:.2f}. This suggests a sudden anomalous spike."
                    ),
                }
            )
        running_sum += amounts[i]

    return anomalies


def detect_balance_mismatch(customer_txns: pd.DataFrame) -> list[dict]:
    """Detect cases where the origin balance drops to 0 after a transaction
    (potential account drain)."""
    anomalies: list[dict] = []
    for _, row in customer_txns.iterrows():
        if (
            row["oldbalanceorg"] > 0
            and row["newbalanceorig"] == 0
            and row["type"] in ("TRANSFER", "CASH_OUT")
        ):
            anomalies.append(
                {
                    "pattern": f"Account drained via {row['type']}",
                    "anomaly_level": "CRITICAL",
                    "explanation": (
                        f"Balance went from {row['oldbalanceorg']:.2f} to 0.00 "
                        f"in a single {row['type']} of {row['amount']:.2f}. "
                        "This may indicate account takeover or fraud."
                    ),
                }
            )
    return anomalies


def assign_overall_anomaly_level(anomalies: list[dict]) -> str:
    """Return the highest anomaly level across all detected anomalies."""
    if not anomalies:
        return "NONE"
    level_order = {"CRITICAL": 3, "HIGH": 2, "MEDIUM": 1, "LOW": 0}
    max_level = max(anomalies, key=lambda a: level_order.get(a["anomaly_level"], 0))
    return max_level["anomaly_level"]


def analyze_customer(
    customer_id: str,
    customer_txns: pd.DataFrame,
    high_value_threshold: float,
) -> list[dict]:
    """Run all anomaly detectors on a single customer's transaction sequence."""
    all_anomalies: list[dict] = []

    all_anomalies.extend(
        detect_repeated_high_value(customer_txns, high_value_threshold)
    )
    all_anomalies.extend(detect_transfer_then_cashout(customer_txns))
    all_anomalies.extend(detect_sudden_increase(customer_txns))
    all_anomalies.extend(detect_balance_mismatch(customer_txns))

    return all_anomalies


def generate_report(results: list[dict]) -> pd.DataFrame:
    """Convert the raw results list into a tidy report DataFrame."""
    report = pd.DataFrame(results)
    # Order by severity
    level_map = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3, "NONE": 4}
    report["_sort"] = report["overall_anomaly_level"].map(level_map)
    report = report.sort_values("_sort").drop(columns="_sort").reset_index(drop=True)
    return report


def main() -> None:
    # Resolve path to the dataset relative to this script
    script_dir = os.path.dirname(os.path.abspath(__file__))
    data_path = os.path.join(script_dir, "Example1.csv")

    print("=" * 70)
    print("  ANOMALOUS TRANSACTION SEQUENCE DETECTION REPORT")
    print("=" * 70)

    # 1. Load dataset
    df = load_dataset(data_path)
    print(f"\nLoaded {len(df)} transactions from {data_path}")

    # 2 & 3. Group by customer and sort by step
    df = group_and_sort(df)

    # Compute high-value threshold from the full dataset
    high_value_threshold = df["amount"].quantile(
        HIGH_VALUE_THRESHOLD_PERCENTILE / 100
    )
    print(f"High-value threshold (P{HIGH_VALUE_THRESHOLD_PERCENTILE}): {high_value_threshold:.2f}")

    # 4-6. Analyse each customer's transaction sequence
    results: list[dict] = []
    grouped = df.groupby("nameorig")

    for customer_id, customer_txns in grouped:
        customer_txns = customer_txns.sort_values("step").reset_index(drop=True)
        anomalies = analyze_customer(
            str(customer_id), customer_txns, high_value_threshold
        )
        overall_level = assign_overall_anomaly_level(anomalies)

        if anomalies:
            for anomaly in anomalies:
                results.append(
                    {
                        "customer_id": customer_id,
                        "sequence_pattern": anomaly["pattern"],
                        "anomaly_level": anomaly["anomaly_level"],
                        "overall_anomaly_level": overall_level,
                        "explanation": anomaly["explanation"],
                    }
                )
        else:
            results.append(
                {
                    "customer_id": customer_id,
                    "sequence_pattern": "Normal",
                    "anomaly_level": "NONE",
                    "overall_anomaly_level": "NONE",
                    "explanation": "No anomalous patterns detected.",
                }
            )

    # 7. Generate report
    report = generate_report(results)

    # Save CSV report
    report_path = os.path.join(script_dir, "anomaly_report.csv")
    report.to_csv(report_path, index=False)
    print(f"\nReport saved to {report_path}")

    # Print summary
    anomalous = report[report["overall_anomaly_level"] != "NONE"]
    unique_anomalous_customers = anomalous["customer_id"].nunique()
    total_customers = report["customer_id"].nunique()

    print(f"\n{'─' * 70}")
    print(f"  SUMMARY")
    print(f"{'─' * 70}")
    print(f"  Total customers analysed : {total_customers}")
    print(f"  Customers with anomalies : {unique_anomalous_customers}")
    print(f"  Total anomaly detections : {len(anomalous)}")
    print()

    level_counts = anomalous["anomaly_level"].value_counts()
    for level in ["CRITICAL", "HIGH", "MEDIUM", "LOW"]:
        if level in level_counts.index:
            print(f"    {level:10s}: {level_counts[level]}")

    print(f"\n{'─' * 70}")
    print("  TOP ANOMALOUS CUSTOMERS (by severity)")
    print(f"{'─' * 70}")

    # Show details for anomalous customers
    for _, row in anomalous.iterrows():
        print(
            f"\n  Customer : {row['customer_id']}\n"
            f"  Pattern  : {row['sequence_pattern']}\n"
            f"  Level    : {row['anomaly_level']}\n"
            f"  Detail   : {row['explanation']}"
        )
        print(f"  {'- ' * 35}")

    print(f"\n{'=' * 70}")
    print("  END OF REPORT")
    print(f"{'=' * 70}")


if __name__ == "__main__":
    main()
