package com.fraud.detection.detector;

import com.fraud.detection.model.AnomalyAlert;
import com.fraud.detection.model.AnomalyAlert.AnomalyType;
import com.fraud.detection.model.AnomalyAlert.Severity;
import com.fraud.detection.model.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects multiple high-value transactions occurring within the same time step.
 * Transactions within the same step (time window) that exceed the threshold
 * are considered suspicious when there are multiple such transactions.
 */
public class MultipleHighValueShortTimeDetector implements AnomalyDetector {

    private static final double HIGH_VALUE_THRESHOLD = 10000.0;
    private static final int MIN_HIGH_VALUE_COUNT = 2;

    @Override
    public List<AnomalyAlert> detect(String customerId, List<Transaction> transactions) {
        List<AnomalyAlert> alerts = new ArrayList<>();

        int i = 0;
        while (i < transactions.size()) {
            int currentStep = transactions.get(i).getStep();
            List<Transaction> sameStepHighValue = new ArrayList<>();

            // Collect all high-value transactions in the same time step
            while (i < transactions.size() && transactions.get(i).getStep() == currentStep) {
                if (transactions.get(i).getAmount() >= HIGH_VALUE_THRESHOLD) {
                    sameStepHighValue.add(transactions.get(i));
                }
                i++;
            }

            if (sameStepHighValue.size() >= MIN_HIGH_VALUE_COUNT) {
                double totalAmount = sameStepHighValue.stream()
                        .mapToDouble(Transaction::getAmount).sum();

                Severity severity = sameStepHighValue.size() >= 3 ? Severity.HIGH : Severity.MEDIUM;

                String details = String.format(
                        "%d high-value transactions (each >= $%.2f) detected in time step %d. "
                                + "Total amount: $%.2f. Multiple large transactions in a short "
                                + "time window indicate potential structuring or layering.",
                        sameStepHighValue.size(), HIGH_VALUE_THRESHOLD,
                        currentStep, totalAmount);

                alerts.add(new AnomalyAlert(customerId,
                        AnomalyType.MULTIPLE_HIGH_VALUE_SHORT_TIME, severity,
                        details, new ArrayList<>(sameStepHighValue)));
            }
        }

        return alerts;
    }

    @Override
    public String getDetectorName() {
        return "Multiple High-Value Transactions in Short Time Detector";
    }
}
