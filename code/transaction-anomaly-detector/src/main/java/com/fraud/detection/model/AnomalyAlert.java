package com.fraud.detection.model;

import java.util.List;

/**
 * Represents a detected anomaly alert for a customer's transaction sequence.
 */
public class AnomalyAlert {

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum AnomalyType {
        REPEATED_HIGH_VALUE("Repeated High-Value Transactions"),
        TRANSFER_THEN_CASHOUT("Transfer Followed by Cash-Out"),
        TRANSFER_TRANSFER_CASHOUT("Transfer → Transfer → Cash-Out Sequence"),
        SUDDEN_AMOUNT_SPIKE("Sudden Increase in Transaction Amount"),
        SUDDEN_TYPE_CHANGE("Sudden Change in Transaction Type"),
        MULTIPLE_HIGH_VALUE_SHORT_TIME("Multiple High-Value Transactions in Short Time");

        private final String description;

        AnomalyType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private final String customerId;
    private final AnomalyType anomalyType;
    private final Severity severity;
    private final String details;
    private final List<Transaction> involvedTransactions;

    public AnomalyAlert(String customerId, AnomalyType anomalyType, Severity severity,
                        String details, List<Transaction> involvedTransactions) {
        this.customerId = customerId;
        this.anomalyType = anomalyType;
        this.severity = severity;
        this.details = details;
        this.involvedTransactions = involvedTransactions;
    }

    public String getCustomerId() {
        return customerId;
    }

    public AnomalyType getAnomalyType() {
        return anomalyType;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getDetails() {
        return details;
    }

    public List<Transaction> getInvolvedTransactions() {
        return involvedTransactions;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("══════════════════════════════════════════════════\n");
        sb.append(String.format("  ANOMALY ALERT [%s]\n", severity));
        sb.append(String.format("  Customer: %s\n", customerId));
        sb.append(String.format("  Type: %s\n", anomalyType.getDescription()));
        sb.append(String.format("  Details: %s\n", details));
        sb.append("  Involved Transactions:\n");
        for (Transaction t : involvedTransactions) {
            sb.append(String.format("    - %s\n", t));
        }
        sb.append("══════════════════════════════════════════════════");
        return sb.toString();
    }
}
