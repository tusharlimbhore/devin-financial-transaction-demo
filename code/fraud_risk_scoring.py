"""
Fraud Risk Scoring Module

Computes risk scores (0-100) for financial transactions and assigns
risk categories (LOW, MEDIUM, HIGH) based on configurable risk factors.

Risk Guidelines:
1. Transactions above 10,000 are high risk.
2. CASH_OUT and TRANSFER are higher risk transaction types.
3. Transactions to new or previously unseen destination accounts are risky.
4. Rapid sequence of transactions from same account increases risk.
5. Fraudulent transactions often involve high amounts followed by cash-out.

Risk Levels:
  - LOW: score < 40
  - MEDIUM: score between 40 and 70
  - HIGH: score > 70
"""

import csv
import os
import sys
from collections import defaultdict


# --- Configuration ---

HIGH_AMOUNT_THRESHOLD = 10000
HIGH_RISK_TYPES = {"CASH_OUT", "TRANSFER"}

# Score weights for each risk factor (must sum to 100 for max possible score)
WEIGHT_AMOUNT = 25
WEIGHT_TYPE = 20
WEIGHT_NEW_DEST = 15
WEIGHT_RAPID_SEQUENCE = 20
WEIGHT_HIGH_AMOUNT_CASHOUT = 20

# Risk category thresholds
LOW_THRESHOLD = 40
HIGH_THRESHOLD = 70


def load_transactions(filepath):
    """Load transactions from a CSV file and return a list of dicts."""
    transactions = []
    with open(filepath, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            row["step"] = int(row["step"])
            row["amount"] = float(row["amount"])
            row["oldbalanceOrg"] = float(row["oldbalanceOrg"])
            row["newbalanceOrig"] = float(row["newbalanceOrig"])
            row["oldbalanceDest"] = float(row["oldbalanceDest"])
            row["newbalanceDest"] = float(row["newbalanceDest"])
            row["isFraud"] = int(row["isFraud"])
            row["isFlaggedFraud"] = int(row["isFlaggedFraud"])
            transactions.append(row)
    return transactions


def _build_account_histories(transactions):
    """Build per-origin-account transaction histories ordered by step."""
    histories = defaultdict(list)
    for idx, txn in enumerate(transactions):
        histories[txn["nameOrig"]].append((idx, txn))
    return histories


def _build_seen_destinations(transactions):
    """Track the first occurrence index for each destination account.

    Returns a dict mapping nameDest -> index of the first transaction
    that targets that destination.
    """
    first_seen = {}
    for idx, txn in enumerate(transactions):
        dest = txn["nameDest"]
        if dest not in first_seen:
            first_seen[dest] = idx
    return first_seen


def score_amount(amount):
    """Score based on transaction amount.

    0 for amounts <= 0, linearly increasing up to HIGH_AMOUNT_THRESHOLD,
    full score for amounts >= HIGH_AMOUNT_THRESHOLD.  Very large amounts
    (> 5x threshold) get the maximum score.
    """
    if amount <= 0:
        return 0
    if amount >= HIGH_AMOUNT_THRESHOLD:
        return WEIGHT_AMOUNT
    return round(WEIGHT_AMOUNT * (amount / HIGH_AMOUNT_THRESHOLD), 2)


def score_type(txn_type):
    """Score based on transaction type.

    CASH_OUT and TRANSFER receive full weight; other types get 0.
    """
    if txn_type in HIGH_RISK_TYPES:
        return WEIGHT_TYPE
    return 0


def score_new_destination(txn_index, dest, first_seen_map):
    """Score based on whether the destination is new/previously unseen.

    A destination is considered 'new' if this transaction is the first
    time we see that destination account in the dataset.
    """
    if first_seen_map.get(dest) == txn_index:
        return WEIGHT_NEW_DEST
    return 0


def score_rapid_sequence(txn_index, origin, account_histories):
    """Score based on rapid sequences from the same origin account.

    If the same origin account has multiple transactions in the same step
    or consecutive steps, the risk increases.  The score scales with the
    number of rapid transactions (capped at full weight).
    """
    history = account_histories.get(origin, [])
    if len(history) <= 1:
        return 0

    current_step = None
    for idx, txn in history:
        if idx == txn_index:
            current_step = txn["step"]
            break

    if current_step is None:
        return 0

    rapid_count = 0
    for idx, txn in history:
        if idx != txn_index and abs(txn["step"] - current_step) <= 1:
            rapid_count += 1

    if rapid_count == 0:
        return 0

    # Scale: 1 nearby txn -> 50% weight, 2 -> 75%, 3+ -> 100%
    fraction = min(1.0, 0.5 + 0.25 * (rapid_count - 1))
    return round(WEIGHT_RAPID_SEQUENCE * fraction, 2)


def score_high_amount_cashout_pattern(txn, txn_index, origin, account_histories):
    """Score based on high-amount-followed-by-cash-out pattern.

    If the origin account has a preceding high-amount transaction and the
    current transaction is a CASH_OUT, this is a strong fraud indicator.
    Also flags CASH_OUT transactions that are themselves high-amount.
    """
    if txn["type"] != "CASH_OUT":
        return 0

    history = account_histories.get(origin, [])

    # Check if any prior transaction from this account had a high amount
    has_prior_high_amount = False
    for idx, prev_txn in history:
        if idx < txn_index and prev_txn["amount"] > HIGH_AMOUNT_THRESHOLD:
            has_prior_high_amount = True
            break

    # Also consider if the cash-out itself is high amount
    is_high_amount = txn["amount"] > HIGH_AMOUNT_THRESHOLD

    if has_prior_high_amount and is_high_amount:
        return WEIGHT_HIGH_AMOUNT_CASHOUT
    elif has_prior_high_amount or is_high_amount:
        return round(WEIGHT_HIGH_AMOUNT_CASHOUT * 0.6, 2)
    else:
        return round(WEIGHT_HIGH_AMOUNT_CASHOUT * 0.2, 2)


def classify_risk(score):
    """Classify a risk score into LOW, MEDIUM, or HIGH."""
    if score < LOW_THRESHOLD:
        return "LOW"
    elif score <= HIGH_THRESHOLD:
        return "MEDIUM"
    else:
        return "HIGH"


def compute_risk_scores(transactions):
    """Compute risk scores for all transactions.

    Returns a list of dicts, each containing the original transaction
    fields plus:
      - risk_score: numeric score 0-100
      - risk_category: LOW / MEDIUM / HIGH
      - risk_factors: dict with individual factor scores
    """
    first_seen_map = _build_seen_destinations(transactions)
    account_histories = _build_account_histories(transactions)

    results = []
    for idx, txn in enumerate(transactions):
        factors = {
            "amount_risk": score_amount(txn["amount"]),
            "type_risk": score_type(txn["type"]),
            "new_destination_risk": score_new_destination(
                idx, txn["nameDest"], first_seen_map
            ),
            "rapid_sequence_risk": score_rapid_sequence(
                idx, txn["nameOrig"], account_histories
            ),
            "high_amount_cashout_risk": score_high_amount_cashout_pattern(
                txn, idx, txn["nameOrig"], account_histories
            ),
        }

        total_score = min(100, round(sum(factors.values()), 2))
        category = classify_risk(total_score)

        results.append(
            {
                **txn,
                "risk_score": total_score,
                "risk_category": category,
                "risk_factors": factors,
            }
        )

    return results


def generate_risk_report(scored_transactions, output_path):
    """Generate a CSV risk report for all scored transactions."""
    fieldnames = [
        "step",
        "type",
        "amount",
        "nameOrig",
        "oldbalanceOrg",
        "newbalanceOrig",
        "nameDest",
        "oldbalanceDest",
        "newbalanceDest",
        "isFraud",
        "isFlaggedFraud",
        "risk_score",
        "risk_category",
        "amount_risk",
        "type_risk",
        "new_destination_risk",
        "rapid_sequence_risk",
        "high_amount_cashout_risk",
    ]

    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for txn in scored_transactions:
            row = {k: txn[k] for k in fieldnames if k not in txn.get("risk_factors", {})}
            row.update(txn["risk_factors"])
            writer.writerow(row)

    return output_path


def print_summary(scored_transactions):
    """Print a summary of risk distribution to stdout."""
    counts = {"LOW": 0, "MEDIUM": 0, "HIGH": 0}
    total_score = 0
    max_score = 0
    min_score = 100

    for txn in scored_transactions:
        counts[txn["risk_category"]] += 1
        total_score += txn["risk_score"]
        max_score = max(max_score, txn["risk_score"])
        min_score = min(min_score, txn["risk_score"])

    total = len(scored_transactions)
    avg_score = round(total_score / total, 2) if total > 0 else 0

    print("=" * 60)
    print("FRAUD RISK SCORING REPORT SUMMARY")
    print("=" * 60)
    print(f"Total transactions analyzed: {total}")
    print(f"Average risk score: {avg_score}")
    print(f"Min risk score: {min_score}")
    print(f"Max risk score: {max_score}")
    print("-" * 60)
    print("Risk Distribution:")
    print(f"  LOW  (score < {LOW_THRESHOLD}):  {counts['LOW']} ({round(100*counts['LOW']/total, 1)}%)")
    print(f"  MEDIUM (score {LOW_THRESHOLD}-{HIGH_THRESHOLD}): {counts['MEDIUM']} ({round(100*counts['MEDIUM']/total, 1)}%)")
    print(f"  HIGH (score > {HIGH_THRESHOLD}): {counts['HIGH']} ({round(100*counts['HIGH']/total, 1)}%)")
    print("-" * 60)

    # Show top 10 highest risk transactions
    sorted_txns = sorted(scored_transactions, key=lambda x: x["risk_score"], reverse=True)
    print("\nTop 10 Highest Risk Transactions:")
    print(f"{'Index':<8}{'Type':<12}{'Amount':>14}{'Origin':<18}{'Dest':<18}{'Score':>8}{'Category':<10}")
    print("-" * 88)
    for i, txn in enumerate(sorted_txns[:10]):
        print(
            f"{i+1:<8}{txn['type']:<12}{txn['amount']:>14,.2f}"
            f"{txn['nameOrig']:<18}{txn['nameDest']:<18}"
            f"{txn['risk_score']:>8.2f}{txn['risk_category']:<10}"
        )
    print("=" * 60)


def main():
    """Main entry point: load data, score, report."""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    input_path = os.path.join(script_dir, "Example1.csv")
    output_path = os.path.join(script_dir, "transaction_risk_report.csv")

    if len(sys.argv) > 1:
        input_path = sys.argv[1]
    if len(sys.argv) > 2:
        output_path = sys.argv[2]

    if not os.path.exists(input_path):
        print(f"Error: Input file not found: {input_path}")
        sys.exit(1)

    print(f"Loading transactions from: {input_path}")
    transactions = load_transactions(input_path)
    print(f"Loaded {len(transactions)} transactions.")

    print("Computing risk scores...")
    scored = compute_risk_scores(transactions)

    print(f"Generating risk report: {output_path}")
    generate_risk_report(scored, output_path)

    print_summary(scored)
    print(f"\nDetailed report saved to: {output_path}")


if __name__ == "__main__":
    main()
