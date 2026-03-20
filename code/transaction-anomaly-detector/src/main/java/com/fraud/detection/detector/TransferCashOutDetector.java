package com.fraud.detection.detector;

import com.fraud.detection.model.AnomalyAlert;
import com.fraud.detection.model.AnomalyAlert.AnomalyType;
import com.fraud.detection.model.AnomalyAlert.Severity;
import com.fraud.detection.model.Transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects suspicious sequences where a TRANSFER is immediately followed by a CASH_OUT.
 * Also detects the extended pattern: TRANSFER → TRANSFER → CASH_OUT.
 * These patterns are common in money laundering schemes.
 */
public class TransferCashOutDetector implements AnomalyDetector {

    @Override
    public List<AnomalyAlert> detect(String customerId, List<Transaction> transactions) {
        List<AnomalyAlert> alerts = new ArrayList<>();

        for (int i = 0; i < transactions.size() - 2; i++) {
            Transaction t1 = transactions.get(i);
            Transaction t2 = transactions.get(i + 1);
            Transaction t3 = transactions.get(i + 2);

            // Detect TRANSFER → TRANSFER → CASH_OUT
            if ("TRANSFER".equals(t1.getType())
                    && "TRANSFER".equals(t2.getType())
                    && "CASH_OUT".equals(t3.getType())) {

                double totalAmount = t1.getAmount() + t2.getAmount() + t3.getAmount();
                String details = String.format(
                        "TRANSFER → TRANSFER → CASH_OUT sequence detected. "
                                + "Amounts: $%.2f → $%.2f → $%.2f (total: $%.2f). "
                                + "This is a high-risk money laundering pattern.",
                        t1.getAmount(), t2.getAmount(), t3.getAmount(), totalAmount);

                alerts.add(new AnomalyAlert(customerId, AnomalyType.TRANSFER_TRANSFER_CASHOUT,
                        Severity.CRITICAL, details, Arrays.asList(t1, t2, t3)));
            }
        }

        // Detect simple TRANSFER → CASH_OUT (not already part of T→T→CO)
        for (int i = 0; i < transactions.size() - 1; i++) {
            Transaction t1 = transactions.get(i);
            Transaction t2 = transactions.get(i + 1);

            if ("TRANSFER".equals(t1.getType()) && "CASH_OUT".equals(t2.getType())) {
                // Skip if this is part of a TRANSFER→TRANSFER→CASH_OUT already detected
                boolean partOfTriple = false;
                if (i > 0 && "TRANSFER".equals(transactions.get(i - 1).getType())) {
                    partOfTriple = true;
                }
                if (i + 2 < transactions.size()
                        && "TRANSFER".equals(transactions.get(i + 1).getType())
                        && "CASH_OUT".equals(transactions.get(i + 2).getType())) {
                    partOfTriple = true;
                }

                if (!partOfTriple) {
                    String details = String.format(
                            "TRANSFER → CASH_OUT sequence detected. "
                                    + "Transfer of $%.2f immediately followed by cash-out of $%.2f. "
                                    + "High risk of fund extraction.",
                            t1.getAmount(), t2.getAmount());

                    alerts.add(new AnomalyAlert(customerId, AnomalyType.TRANSFER_THEN_CASHOUT,
                            Severity.HIGH, details, Arrays.asList(t1, t2)));
                }
            }
        }

        return alerts;
    }

    @Override
    public String getDetectorName() {
        return "Transfer → Cash-Out Sequence Detector";
    }
}
