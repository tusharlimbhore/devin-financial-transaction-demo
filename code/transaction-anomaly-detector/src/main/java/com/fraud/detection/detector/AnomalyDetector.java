package com.fraud.detection.detector;

import com.fraud.detection.model.AnomalyAlert;
import com.fraud.detection.model.Transaction;

import java.util.List;

/**
 * Interface for anomaly detection strategies.
 * Each implementation detects a specific type of suspicious pattern.
 */
public interface AnomalyDetector {

    /**
     * Analyzes a customer's transaction sequence and returns any detected anomalies.
     *
     * @param customerId the customer identifier
     * @param transactions the customer's transactions sorted by step
     * @return list of detected anomaly alerts
     */
    List<AnomalyAlert> detect(String customerId, List<Transaction> transactions);

    /**
     * Returns a description of the anomaly pattern this detector looks for.
     *
     * @return detector description
     */
    String getDetectorName();
}
