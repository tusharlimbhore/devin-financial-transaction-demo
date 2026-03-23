package com.fraud.detection.model;

/**
 * Represents a single financial transaction parsed from CSV data.
 */
public class Transaction {

    private final int step;
    private final String type;
    private final double amount;
    private final String nameOrig;
    private final double oldBalanceOrig;
    private final double newBalanceOrig;
    private final String nameDest;
    private final double oldBalanceDest;
    private final double newBalanceDest;
    private final int isFraud;
    private final int isFlaggedFraud;

    public Transaction(int step, String type, double amount, String nameOrig,
                       double oldBalanceOrig, double newBalanceOrig,
                       String nameDest, double oldBalanceDest, double newBalanceDest,
                       int isFraud, int isFlaggedFraud) {
        this.step = step;
        this.type = type;
        this.amount = amount;
        this.nameOrig = nameOrig;
        this.oldBalanceOrig = oldBalanceOrig;
        this.newBalanceOrig = newBalanceOrig;
        this.nameDest = nameDest;
        this.oldBalanceDest = oldBalanceDest;
        this.newBalanceDest = newBalanceDest;
        this.isFraud = isFraud;
        this.isFlaggedFraud = isFlaggedFraud;
    }

    public int getStep() {
        return step;
    }

    public String getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public String getNameOrig() {
        return nameOrig;
    }

    public double getOldBalanceOrig() {
        return oldBalanceOrig;
    }

    public double getNewBalanceOrig() {
        return newBalanceOrig;
    }

    public String getNameDest() {
        return nameDest;
    }

    public double getOldBalanceDest() {
        return oldBalanceDest;
    }

    public double getNewBalanceDest() {
        return newBalanceDest;
    }

    public int getIsFraud() {
        return isFraud;
    }

    public int getIsFlaggedFraud() {
        return isFlaggedFraud;
    }

    @Override
    public String toString() {
        return String.format("Transaction{step=%d, type='%s', amount=%.2f, from='%s', to='%s'}",
                step, type, amount, nameOrig, nameDest);
    }
}
