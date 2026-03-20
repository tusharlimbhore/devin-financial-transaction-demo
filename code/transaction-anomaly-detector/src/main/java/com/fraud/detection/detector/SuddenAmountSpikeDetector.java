package com.fraud.detection.detector;

import com.fraud.detection.model.AnomalyAlert;
import com.fraud.detection.model.AnomalyAlert.AnomalyType;
import com.fraud.detection.model.AnomalyAlert.Severity;
import com.fraud.detection.model.Transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects sudden spikes in transaction amounts for a customer.
 * A spike is identified when a transaction amount is significantly larger
 * than the customer's running average (by a configurable multiplier).
 */
public class SuddenAmountSpikeDetector implements AnomalyDetector {

    private static final double SPIKE_MULTIPLIER = 3.0;
    private static final double MIN_AVERAGE_THRESHOLD = 100.0;

    @Override
    public List<AnomalyAlert> detect(String customerId, List<Transaction> transactions) {
        List<AnomalyAlert> alerts = new ArrayList<>();

        if (transactions.size() < 2) {
            return alerts;
        }

        double runningSum = 0;

        for (int i = 0; i < transactions.size(); i++) {
            Transaction current = transactions.get(i);

            if (i > 0) {
                double runningAvg = runningSum / i;

                if (runningAvg >= MIN_AVERAGE_THRESHOLD
                        && current.getAmount() >= runningAvg * SPIKE_MULTIPLIER) {

                    Transaction previous = transactions.get(i - 1);
                    double spikeRatio = current.getAmount() / runningAvg;

                    Severity severity;
                    if (spikeRatio >= 10.0) {
                        severity = Severity.CRITICAL;
                    } else if (spikeRatio >= 5.0) {
                        severity = Severity.HIGH;
                    } else {
                        severity = Severity.MEDIUM;
                    }

                    String details = String.format(
                            "Sudden amount spike detected. Current transaction: $%.2f, "
                                    + "running average: $%.2f (%.1fx increase). "
                                    + "Previous transaction: $%.2f.",
                            current.getAmount(), runningAvg, spikeRatio, previous.getAmount());

                    alerts.add(new AnomalyAlert(customerId, AnomalyType.SUDDEN_AMOUNT_SPIKE,
                            severity, details, Arrays.asList(previous, current)));
                }
            }

            runningSum += current.getAmount();
        }

        return alerts;
    }

    @Override
    public String getDetectorName() {
        return "Sudden Amount Spike Detector";
    }
}
