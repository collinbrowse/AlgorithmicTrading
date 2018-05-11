import com.optionscity.freeway.api.IContainer;
import org.optionscity.uchicago.common.case1.Case1Ticker;
import org.optionscity.uchicago.common.case2.*;
import org.optionscity.uchicago.common.order.Order;
import org.optionscity.uchicago.common.order.OrderStatus;
import org.optionscity.uchicago.common.order.OrderTransaction;
import org.optionscity.uchicago.job.case2.AbstractCase2Job;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CancellationException;

/**
 * An implementation of Case 2 in the 2016 University of Chicago Algorithmic Trading competition
 * School - St. Lawrence University
 * Team Members - Collin Browse, Zengjing Chu, Sirius Amerman
 * Algorithm - Zenhgjing Chu
 * Implementation - Collin Browse
 *
 * Purpose - optimally trades 5 stocks on the exchange as well as optimally accepts/rejects off
 * block orders
 */

public class STL1Case2Job extends AbstractCase2Job {

    private ArrayList<Double> UCXPrices = new ArrayList<>();
    private ArrayList<Double> JLGPrices = new ArrayList<>();
    private ArrayList<Double> MLPrices = new ArrayList<>();
    private ArrayList<Double> OILPrices = new ArrayList<>();
    private ArrayList<Double> WTRPrices = new ArrayList<>();
    private ArrayList<Integer> sharesNotFilled = new ArrayList<>();
    private ArrayList<Integer> optimalShares = new ArrayList<>();
    private ArrayList<Double> optimalWeights = new ArrayList<>();
    private ArrayList<Case2Ticker> case2Stocks = new ArrayList<>();
    private ArrayList<Integer> sharesToLiquidate = new ArrayList<>();
    private HashMap<Integer, ArrayList<Double>> possibleWeights = new HashMap<>();
    private int tick;
    private double maxPortfolioValue;
    private String round;

    /**
     * This method is called upon initialization of STL1Case2Job
     * @param container
     */
    public void start(IContainer container) {

        // Initialize Instance data
        tick = 0;
        maxPortfolioValue = 100000;
        round = container.getVariable("round number");

        case2Stocks.add(Case2Ticker.UCX);
        case2Stocks.add(Case2Ticker.JLG);
        case2Stocks.add(Case2Ticker.ML);
        case2Stocks.add(Case2Ticker.OIL);
        case2Stocks.add(Case2Ticker.WTR);

        for (Case2Ticker stock : case2Stocks) {
            optimalWeights.add(0.0);
            optimalShares.add(0);
            sharesNotFilled.add(0);
            sharesToLiquidate.add(0);
        }

        // Create all valid combinations of weights
        possibleWeights = new HashMap<>();
        int count = 0;
        for (double a = 0.7; a >= 0.1; a -= 0.1) {
            for (double b = 0.7; b >= 0.1; b -= 0.1) {
                if (a + b < 0.9) {
                    for (double c = 0.7; c >= 0.1; c -= 0.1) {
                        if (a + b + c < 0.9) {
                            for (double d = 0.7; d >= 0.1; d -= 0.1) {
                                if (a + b + c + d < 0.9) {
                                    ArrayList temp = new ArrayList<>();
                                    temp.add(a);                    // Index 0 - weight of UCX
                                    temp.add(b);                    // Index 1 - weight of JLG
                                    temp.add(c);                    // Index 2 - weight of ML
                                    temp.add(d);                    // Index 3 - weight of OIL
                                    temp.add(1 - (a + b + c + d));  // Index 4 - weight of WTR
                                    possibleWeights.put(count, temp);
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Called at every tick
     * Implements STL1 trading algorithm for on the exchange trades
     */
    @Override
    public void onTick() {

        tick++;

        // Keep track of past prices
        UCXPrices.add(getPrice(Case2Ticker.UCX));
        JLGPrices.add(getPrice(Case2Ticker.JLG));
        MLPrices.add(getPrice(Case2Ticker.ML));
        OILPrices.add(getPrice(Case2Ticker.OIL));
        WTRPrices.add(getPrice(Case2Ticker.WTR));

        // Perform Optimization of weights up until tick 510
        if (tick <= 510) {

            updateSharesNotFilled();
            // Every 15 ticks update optimization
            if (tick % 15 == 0 && tick > 0) {

                // Update how much we are willing to spend based on current tick
                if (maxPortfolioValue <= 1400000)
                    maxPortfolioValue += 100000;

                // Update optimal weights/shares
                optimizeWeights();
                optimizeShares();
                updateSharesNotFilled();
            }

            else {

                // On the exchange
                orderShares(14);
                updateSharesNotFilled();
            }
        }

        else {
            // At tick 510, begin liquidation process
            if (tick == 511)
                setSharesToLiquidate();

            // Order proper amount of shares to liquidate
            liquidateShares();
        }
    }

    /**
     * Implements STL1 trading algorithm for off the exchange private orders
     * @param ticker - The stock in question
     * @param counterParty - The party offering the trade
     * @param quantity - The amount of the stock that is being offered
     * @param price - The price of the stock
     * @return - Whether or not the trade is accepted
     */
    @Override
    public boolean onPrivateOrder(Case2Ticker ticker, CounterParty counterParty, int quantity, double price) {

        // Only accept Private Orders until liquidation phase
        if (tick <= 510) {

            int count = 0;
            // For every stock
            for (Case2Ticker stock : case2Stocks) {
                if (Math.abs(sharesNotFilled.get(count)) - quantity < Math.abs(sharesNotFilled.get(count))) {
                    if (stock == Case2Ticker.UCX) {
                        return (price < getPrice(stock) + 0.05);
                    } else
                        return (price < getPrice(stock) + (0.25 * Math.pow(1.001, price)));
                }
                count++;
            }
        }

        // If we get here then the order did not meet requirements
        return false;
    }

    /**
     * Implements STL1 trading algorithm for off the exchange competitive orders
     * @param case2Ticker - The stock in question
     * @param counterParty - The party offering the trade
     * @param i - The amount of the stock that is being offered
     * @return - The price that is acceptable for the trade
     */
    @Override
    public double onCompetitiveOrder(Case2Ticker case2Ticker, CounterParty counterParty, int i) {

        // Only accept Competitive Orders until liquidation phase
        if (tick <= 510) {

            int count = 0;
            for (Case2Ticker stock : case2Stocks) {
                if (Math.abs(sharesNotFilled.get(count)) - i < Math.abs(sharesNotFilled.get(count))) {
                    if (stock == Case2Ticker.UCX) {
                        return getPrice(stock) + 0.05 - getPrice(stock) * 0.025;
                    } else
                        return getPrice(stock) + 0.025 * Math.pow(1.001, i) - getPrice(stock) * 0.025;
                }
                count++;
            }
        }

        // If we get here then the order did not meet requirements
        return -1;
    }

    /**
     * Order shares (optimal number of shares - current position)
     * Orders over/under +-1000 shares will be rejected
     * @param divisor - a divisor to spread out purchase of shares
     */
    private void orderShares(int divisor) {
        int count = 0;
        for (Case2Ticker stock : case2Stocks) {
            if (sharesNotFilled.get(count) < 0)
                order(stock, (Math.max(-1000, sharesNotFilled.get(count))) / divisor);
            else if (sharesNotFilled.get(count) > 0)
                order(stock, (Math.min(1000, sharesNotFilled.get(count))) / divisor);
            count++;
        }
    }

    /**
     * Order shares that will liquidate the current position in 90 ticks
     */
    private void liquidateShares() {
        int count = 0;
        for (Case2Ticker stock : case2Stocks) {
            order(stock, sharesToLiquidate.get(count));
            count++;
        }
    }

    /**
     * Set up program state to begin liquidation phase
     */
    private void setSharesToLiquidate() {
        int count = 0;
        for (Case2Ticker stock : case2Stocks) {
            sharesToLiquidate.set(count, (-1*getPosition(stock)) / 90);
            count++;
        }
    }

    /**
     * Update the number of shares needed to get the position in each stock to its desired position
     */
    private void updateSharesNotFilled() {
        int count = 0;
        for (Case2Ticker stock : case2Stocks) {
            if (optimalShares.get(count) > 20000)
                optimalShares.set(count, 20000);
            else
                sharesNotFilled.set(count, optimalShares.get(count) - getPosition(stock));
            count ++;
        }
    }

    /**
     * Compute the optimal position for each stock
     */
    private void optimizeShares() {
        for (int i = 0; i < case2Stocks.size(); i++)
            optimalShares.set(i, (int) Math.floor(optimalWeights.get(i)*maxPortfolioValue));
    }

    /**
     * Compute the optimal weight for each stock in the portfolio
     */
    private void optimizeWeights() {

        // Because no closed form solution exists, try all valid weight combinations
        // Return the optimal combination with an Array List

        // Initialize each index of optimalWeights
        double max = -1000000;

        for (int i = 0; i < possibleWeights.size(); i ++) {
            double w1 = possibleWeights.get(i).get(0);
            double w2 = possibleWeights.get(i).get(1);
            double w3 = possibleWeights.get(i).get(2);
            double w4 = possibleWeights.get(i).get(3);
            double w5 = possibleWeights.get(i).get(4);
            double Ep = expectedPortfolioValue(w1, w2, w3, w4, w5);
            double cost = portfolioCost(w1, w2, w3, w4, w5);
            double sigma = portfolioSD(w1, w2, w3, w4, w5);

            // Record the optimal Weights for each stock
            if ((Ep-cost)/sigma > max) {
                max = (Ep - cost) / sigma;
                optimalWeights.set(0,w1);
                optimalWeights.set(1,w2);
                optimalWeights.set(2,w3);
                optimalWeights.set(3,w4);
                optimalWeights.set(4,w5);
            }
        }
    }

    /**
     * Compute the standard deviation of a set of Doubles
     * @param w1 - weight of UCX
     * @param w2 - weight of JLG
     * @param w3 - weight of ML
     * @param w4 - weight of OIL
     * @param w5 - weight of WTR
     * @return - the square root of the variance
     */
    private double portfolioSD(double w1, double w2, double w3, double w4, double w5) {

        // Volatility in each stock changes based on round
        double sumSD = 0;
        if (!round.equals("3")) {
            sumSD += Math.pow(w1, 2) * Math.pow(.00443333, 2);
            sumSD += Math.pow(w2, 2) * Math.pow(.00793333, 2);
            sumSD += Math.pow(w3, 3) * Math.pow(.01633333, 2);
            sumSD += Math.pow(w4, 4) * Math.pow(.00653333, 2);
            sumSD += Math.pow(w5, 5) * Math.pow(.00125, 2);
        }

        else {
            sumSD += Math.pow(w1, 2) * Math.pow(.00532, 2);
            sumSD += Math.pow(w2, 2) * Math.pow(.00952, 2);
            sumSD += Math.pow(w3, 3) * Math.pow(.0196, 2);
            sumSD += Math.pow(w4, 4) * Math.pow(.00784, 2);
            sumSD += Math.pow(w5, 5) * Math.pow(.0015, 2);
        }

        return Math.sqrt(sumSD);
    }

    /**
     * Compute the Cost associated with trading each stock to its corresponding weight
     * @param w1 - weight of UCX
     * @param w2 - weight of JLG
     * @param w3 - weight of ML
     * @param w4 - weight of OIL
     * @param w5 - weight of WTR
     * @return - the associate cost
     */
    private double portfolioCost(double w1, double w2, double w3, double w4, double w5) {
        double portfolioCost = 0;
        portfolioCost += costOfStock(Case2Ticker.UCX, w1);
        portfolioCost += costOfStock(Case2Ticker.JLG, w2);
        portfolioCost += costOfStock(Case2Ticker.ML, w3);
        portfolioCost += costOfStock(Case2Ticker.OIL, w4);
        portfolioCost += costOfStock(Case2Ticker.WTR, w5);
        portfolioCost *= 0.25;
        return portfolioCost;
    }

    /**
     * Compute the cost that would incur if associate shares of stock at weight were purchase
     * @param stock - The ticker of the stock
     * @param weight - The weight of the stock in the portfolio
     * @return
     */
    private double costOfStock(Case2Ticker stock, double weight) {

        if (stock.equals(Case2Ticker.UCX)){
            return 0.05 * Math.abs((maxPortfolioValue * weight / getPrice(stock) - getPosition(stock)));
        }
        return 0.25 * Math.pow(1.001, Math.abs((maxPortfolioValue * weight / getPrice(stock) - getPosition(stock))));
    }

    /**
     * Compute the Average Return of the portfolio given certain weights
     * Return of each stock depends on return of UCX (index)
     * @param w1 - weight of UCX
     * @param w2 - weight of JLG
     * @param w3 - weight of ML
     * @param w4 - weight of OIL
     * @param w5 - weight of WTR
     * @return - the expected value of the portfolio
     */
    private double expectedPortfolioValue(double w1, double w2, double w3, double w4, double w5) {
        return expectedUCX()*(w1 + 1.5*w2 + 2*w3 + 0.9*w4 + 0.2*w5)*UCXPrices.get(UCXPrices.size()-1);
    }

    /**
     * Compute the expected return of UCX (index)
     * @return - the expected return of UCS
     */
    private double expectedUCX() {

        double totalUCXReturn = 0;
        for (int i = 0; i < UCXPrices.size(); i++) {
            totalUCXReturn += (UCXPrices.get(UCXPrices.size()-1) - UCXPrices.get(UCXPrices.size()-2)) / UCXPrices.get(UCXPrices.size()-2);
        }
        return totalUCXReturn/UCXPrices.size();
    }

}