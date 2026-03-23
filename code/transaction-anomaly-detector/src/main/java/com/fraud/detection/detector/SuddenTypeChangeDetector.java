package com.fraud.detection.detector;

import com.fraud.detection.model.AnomalyAlert;
import com.fraud.detection.model.AnomalyAlert.AnomalyType;
import com.fraud.detection.model.AnomalyAlert.Severity;
import com.fraud.detection.model.Transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects sudden changes in transaction type patterns.
 * A sudden type change is flagged when a customer rapidly switches
 * between different transaction types (e.g., from repeated PAYMENTs to TRANSFER).
 */
public class SuddenTypeChangeDetector implements AnomalyDetector {

    private static final int CONSISTENT_TYPE_WINDOW = 3;

    // Type transitions considered suspicious
    private static final Set<String> SUSPICIOUS_TRANSITIONS = new HashSet<>();

    static {
        SUSPICIOUS_TRANSITIONS.add("PAYMENT->TRANSFER");
        SUSPICIOUS_TRANSITIONS.add("PAYMENT->CASH_OUT");
        SUSPICIOUS_TRANSITIONS.add("DEBIT->TRANSFER");
        SUSPICIOUS_TRANSITIONS.add("DEBIT->CASH_OUT");
    }

    @Override
    public List<AnomalyAlert> detect(String customerId, List<Transaction> transactions) {
        List<AnomalyAlert> alerts = new ArrayList<>();

        if (transactions.size() < CONSISTENT_TYPE_WINDOW + 1) {
            return alerts;
        }

        for (int i = CONSISTENT_TYPE_WINDOW; i < transactions.size(); i++) {
            // Check if the previous window has a consistent type
            String windowType = transactions.get(i - 1).getType();
            boolean windowConsistent = true;

            for (int j = i - CONSISTENT_TYPE_WINDOW; j < i; j++) {
                if (!transactions.get(j).getType().equals(windowType)) {
                    windowConsistent = false;
                    break;
                }
            }

            if (windowConsistent) {
                Transaction current = transactions.get(i);
                String transition = windowType + "->" + current.getType();

                if (!current.getType().equals(windowType)
                        && SUSPICIOUS_TRANSITIONS.contains(transition)) {

                    String details = String.format(
                            "Sudden transaction type change detected. "
                                    + "Customer had %d consecutive %s transactions, "
                                    + "then switched to %s (amount: $%.2f). "
                                    + "Transition '%s' is flagged as suspicious.",
                            CONSISTENT_TYPE_WINDOW, windowType,
                            current.getType(), current.getAmount(), transition);

                    Transaction previous = transactions.get(i - 1);
                    alerts.add(new AnomalyAlert(customerId, AnomalyType.SUDDEN_TYPE_CHANGE,
                            Severity.MEDIUM, details, Arrays.asList(previous, current)));
                }
            }
        }

        return alerts;
    }

    @Override
    public String getDetectorName() {
        return "Sudden Transaction Type Change Detector";
    }
}
