package com.fraud.detection.detector;

import com.fraud.detection.model.AnomalyAlert;
import com.fraud.detection.model.AnomalyAlert.AnomalyType;
import com.fraud.detection.model.AnomalyAlert.Severity;
import com.fraud.detection.model.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects repeated high-value transactions in sequence for a customer.
 * A high-value transaction is one that exceeds the configured threshold.
 * Consecutive high-value transactions are flagged as suspicious.
 */
public class RepeatedHighValueDetector implements AnomalyDetector {

    private static final double HIGH_VALUE_THRESHOLD = 10000.0;
    private static final int MIN_CONSECUTIVE_HIGH_VALUE = 2;

    @Override
    public List<AnomalyAlert> detect(String customerId, List<Transaction> transactions) {
        List<AnomalyAlert> alerts = new ArrayList<>();
        List<Transaction> consecutiveHighValue = new ArrayList<>();

        for (Transaction tx : transactions) {
            if (tx.getAmount() >= HIGH_VALUE_THRESHOLD) {
                consecutiveHighValue.add(tx);
            } else {
                if (consecutiveHighValue.size() >= MIN_CONSECUTIVE_HIGH_VALUE) {
                    alerts.add(createAlert(customerId, consecutiveHighValue));
                }
                consecutiveHighValue.clear();
            }
        }

        // Check trailing sequence
        if (consecutiveHighValue.size() >= MIN_CONSECUTIVE_HIGH_VALUE) {
            alerts.add(createAlert(customerId, consecutiveHighValue));
        }

        return alerts;
    }

    private AnomalyAlert createAlert(String customerId, List<Transaction> transactions) {
        double totalAmount = transactions.stream().mapToDouble(Transaction::getAmount).sum();
        Severity severity = transactions.size() >= 3 ? Severity.HIGH : Severity.MEDIUM;

        String details = String.format(
                "%d consecutive high-value transactions detected (total: $%.2f). "
                        + "Each transaction exceeds $%.2f threshold.",
                transactions.size(), totalAmount, HIGH_VALUE_THRESHOLD);

        return new AnomalyAlert(customerId, AnomalyType.REPEATED_HIGH_VALUE, severity,
                details, new ArrayList<>(transactions));
    }

    @Override
    public String getDetectorName() {
        return "Repeated High-Value Transaction Detector";
    }
}
