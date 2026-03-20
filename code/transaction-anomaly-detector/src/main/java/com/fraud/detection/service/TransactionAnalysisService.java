package com.fraud.detection.service;

import com.fraud.detection.detector.AnomalyDetector;
import com.fraud.detection.detector.MultipleHighValueShortTimeDetector;
import com.fraud.detection.detector.RepeatedHighValueDetector;
import com.fraud.detection.detector.SuddenAmountSpikeDetector;
import com.fraud.detection.detector.SuddenTypeChangeDetector;
import com.fraud.detection.detector.TransferCashOutDetector;
import com.fraud.detection.model.AnomalyAlert;
import com.fraud.detection.model.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core service that orchestrates anomaly detection across all customers.
 * Groups transactions by customer, sorts them by time step, and runs
 * all registered anomaly detectors against each customer's sequence.
 */
public class TransactionAnalysisService {

    private final List<AnomalyDetector> detectors;

    public TransactionAnalysisService() {
        this.detectors = new ArrayList<>();
        registerDefaultDetectors();
    }

    private void registerDefaultDetectors() {
        detectors.add(new RepeatedHighValueDetector());
        detectors.add(new TransferCashOutDetector());
        detectors.add(new SuddenAmountSpikeDetector());
        detectors.add(new SuddenTypeChangeDetector());
        detectors.add(new MultipleHighValueShortTimeDetector());
    }

    /**
     * Analyzes all transactions and returns detected anomalies grouped by customer.
     *
     * @param transactions list of all transactions
     * @return map of customer ID to their anomaly alerts
     */
    public Map<String, List<AnomalyAlert>> analyzeTransactions(List<Transaction> transactions) {
        // Group transactions by customer (nameOrig)
        Map<String, List<Transaction>> customerTransactions = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getNameOrig));

        Map<String, List<AnomalyAlert>> allAlerts = new LinkedHashMap<>();

        for (Map.Entry<String, List<Transaction>> entry : customerTransactions.entrySet()) {
            String customerId = entry.getKey();
            List<Transaction> customerTxns = entry.getValue();

            // Sort by step (time window)
            customerTxns.sort(Comparator.comparingInt(Transaction::getStep));

            // Run all detectors
            List<AnomalyAlert> customerAlerts = new ArrayList<>();
            for (AnomalyDetector detector : detectors) {
                List<AnomalyAlert> detected = detector.detect(customerId, customerTxns);
                customerAlerts.addAll(detected);
            }

            if (!customerAlerts.isEmpty()) {
                allAlerts.put(customerId, customerAlerts);
            }
        }

        return allAlerts;
    }

    /**
     * Generates a summary report of the analysis results.
     *
     * @param transactions all transactions analyzed
     * @param alerts detected anomaly alerts grouped by customer
     * @return formatted summary report string
     */
    public String generateSummaryReport(List<Transaction> transactions,
                                        Map<String, List<AnomalyAlert>> alerts) {
        StringBuilder report = new StringBuilder();

        report.append("\n");
        report.append("╔══════════════════════════════════════════════════════════════╗\n");
        report.append("║       TRANSACTION ANOMALY DETECTION - SUMMARY REPORT        ║\n");
        report.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        // Overall statistics
        long totalCustomers = transactions.stream()
                .map(Transaction::getNameOrig)
                .distinct()
                .count();

        int totalAlerts = alerts.values().stream().mapToInt(List::size).sum();

        long criticalCount = alerts.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.getSeverity() == AnomalyAlert.Severity.CRITICAL)
                .count();
        long highCount = alerts.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.getSeverity() == AnomalyAlert.Severity.HIGH)
                .count();
        long mediumCount = alerts.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.getSeverity() == AnomalyAlert.Severity.MEDIUM)
                .count();
        long lowCount = alerts.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.getSeverity() == AnomalyAlert.Severity.LOW)
                .count();

        report.append("── Overview ───────────────────────────────────────────────────\n");
        report.append(String.format("  Total Transactions Analyzed : %d\n", transactions.size()));
        report.append(String.format("  Total Unique Customers      : %d\n", totalCustomers));
        report.append(String.format("  Customers with Anomalies    : %d\n", alerts.size()));
        report.append(String.format("  Total Anomaly Alerts        : %d\n", totalAlerts));
        report.append("\n");

        report.append("── Severity Breakdown ─────────────────────────────────────────\n");
        report.append(String.format("  CRITICAL : %d\n", criticalCount));
        report.append(String.format("  HIGH     : %d\n", highCount));
        report.append(String.format("  MEDIUM   : %d\n", mediumCount));
        report.append(String.format("  LOW      : %d\n", lowCount));
        report.append("\n");

        // Alert type breakdown
        Map<AnomalyAlert.AnomalyType, Long> typeBreakdown = alerts.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(AnomalyAlert::getAnomalyType, Collectors.counting()));

        report.append("── Anomaly Type Breakdown ─────────────────────────────────────\n");
        for (Map.Entry<AnomalyAlert.AnomalyType, Long> entry : typeBreakdown.entrySet()) {
            report.append(String.format("  %-45s : %d\n",
                    entry.getKey().getDescription(), entry.getValue()));
        }
        report.append("\n");

        // Registered detectors
        report.append("── Active Detectors ───────────────────────────────────────────\n");
        for (AnomalyDetector detector : detectors) {
            report.append(String.format("  • %s\n", detector.getDetectorName()));
        }
        report.append("\n");

        return report.toString();
    }

    public List<AnomalyDetector> getDetectors() {
        return detectors;
    }
}
