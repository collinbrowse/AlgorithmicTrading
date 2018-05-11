import com.optionscity.freeway.api.IContainer;
import org.optionscity.uchicago.common.case1.Case1Ticker;
import org.optionscity.uchicago.job.case1.AbstractCase1Job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class STL1Case1Job extends AbstractCase1Job {

    private ArrayList<Double> NAPA;     // All ticks of NAPA
    private ArrayList<Double> NAPB;     // All ticks of NAPB
    private ArrayList<Double> YBERA;    // All ticks of YBERA
    private ArrayList<Double> YBERB;    // All ticks of YBERB

    private ArrayList<Double> W_tNAP;   // NAPA-NAPB at each tick
    private ArrayList<Double> W_t1NAP;  // W_t at current tick - W_t at previous tick
    private ArrayList<Double> W_tYBER;  //  YBERA-YBERB at each tick
    private ArrayList<Double> W_t1YBER; // W_t at current tick - W_t at previous tick

    private ArrayList<Double> recentTicksNAP;
    private ArrayList<Double> recentTicksYBER;

    private HashMap<Case1Ticker, Integer> convergenceBook;  // Keeps track of shares bought using the reversion model
    private HashMap<Case1Ticker, Integer> reversionBook;    // Keeps track of shares bought using the convergence model

    private int x;      // Optimal shares
    private int y;      // Optimal shares
    private int tick;   // The current tick
    private boolean stop;

    private double mNAP;    // m for NAP calculated in the mean reversion model
    private double mYBER;   // m for YBER calculated in the mean reversion model
    private double sdNAP;    // SD for NAP
    private double sdYBER;   // SD for YBER

    private double previousNAPB;    // Keep track of the price of NAPB(tick) at the previous tick
    private double previousYBERB;   // Keep track of the price of YBERB(lagger) at the previous tick


    /**
     * Called at the start of the job and only run once
     * @param container
     */
    public void start(IContainer container) {
        NAPA = new ArrayList<>();
        NAPB = new ArrayList<>();
        YBERA = new ArrayList<>();
        YBERB = new ArrayList<>();
        W_tNAP = new ArrayList<>();
        W_t1NAP = new ArrayList<>();
        W_tYBER = new ArrayList<>();
        W_t1YBER = new ArrayList<>();
        recentTicksNAP = new ArrayList();
        recentTicksYBER = new ArrayList();
        convergenceBook = new HashMap<>();
        reversionBook = new HashMap<>();
        tick = -1;
        stop = false;
    }

    /**
     * Called on every tick
     * Allows for orders to be placed on the exchange
     */
    @Override
    public void onTick() {

        tick++;             // Ticks are 0-indexed

        // Only on the first tick
        if (tick == 0) {
            previousNAPB = getPrice(Case1Ticker.NAPB);
            previousYBERB = getPrice(Case1Ticker.YBERB);
        }

        // On every tick
        double NAPAcurr = getPrice(Case1Ticker.NAPA);
        NAPA.add(NAPAcurr);
        double NAPBcurr = getPrice(Case1Ticker.NAPB);
        NAPB.add(NAPBcurr);
        double YBERAcurr = getPrice(Case1Ticker.YBERA);
        YBERA.add(YBERAcurr);
        double YBERBcurr = getPrice(Case1Ticker.YBERB);
        YBERB.add(YBERBcurr);
        W_tNAP.add(NAPAcurr - previousNAPB);
        W_tYBER.add(YBERAcurr - previousYBERB);

        // Up until tick 50
        if (tick > 0 && tick <= 50) {
            W_t1NAP.add(W_tNAP.get(tick) - W_tNAP.get(tick - 1));
            W_t1YBER.add(W_tYBER.get(tick) - W_tYBER.get(tick - 1));
        }

        // On tick 50
        if (tick == 50) {
            mNAP = computeM(W_tNAP, W_t1NAP);
            mYBER = computeM(W_tYBER, W_t1YBER);
            sdNAP = computeSD(W_tNAP);
            sdYBER = computeSD(W_tYBER);
        }

        if (tick % 2 == 1 && tick > 50) {
            clearReversionPosition();

        }
        if (Math.abs(getPosition(Case1Ticker.NAPA)) > 7000) {
            shrinkPosition();
        }
        if (Math.abs(getPosition(Case1Ticker.NAPB)) > 7000) {
            shrinkPosition();
        }
        if (Math.abs(getPosition(Case1Ticker.YBERA)) > 7000) {
            shrinkPosition();
        }
        if (Math.abs(getPosition(Case1Ticker.YBERB)) > 7000) {
            shrinkPosition();
        }

        // From tick 6-500
        if (tick > 6) {
            if (tick > 6 && tick < 13) {
                for (int i = 0; i < 6; i++) {
                    recentTicksNAP.add(W_tNAP.get(W_tNAP.size() - i - 1));
                    recentTicksYBER.add(W_tYBER.get(W_tYBER.size() - i - 1));
                }
            }
            if (tick > 13) {
                // Keep track of past 6 ticks separately to make code easier to read
                for (int i = 0; i < 6; i++) {
                    recentTicksNAP.set(i + 1, W_tNAP.get(W_tNAP.size() - i - 1));
                    recentTicksYBER.set(i + 1, W_tYBER.get(W_tYBER.size() - i - 1));
                }

                // From tick 50-600
                if (tick >= 50) {

                    Random rand = new Random();
                    // Get the predicted price of the lagger (NAPB)
                    double forecastNAP = -mNAP * W_tNAP.get(tick - 1) + sdNAP * rand.nextGaussian();
                    double W_t1NAP = forecastNAP + W_tNAP.get(tick - 1);

                    // // Get the predicted price of the lagger (YBERB)
                    double forecastYBER = -mYBER * (YBERAcurr - YBERBcurr) + sdYBER * rand.nextGaussian();
                    double W_t1YBER = forecastYBER + W_tYBER.get(tick - 1);

                    // Optimize profit with respect to number of shares
                    calculateShares(W_tNAP.get(tick - 1), W_tYBER.get(tick - 1), W_t1NAP, W_t1YBER);

                    // trade on tick
                    if (((NAPAcurr > NAPBcurr) && (Math.abs(forecastNAP) > Math.abs(W_t1NAP)))
                            || ((NAPAcurr < NAPBcurr) && (Math.abs(forecastNAP) < Math.abs(W_t1NAP)))) {
                        if (tick % 2 == 0) {
                            order(Case1Ticker.NAPA, -x);
                            order(Case1Ticker.NAPB, x);
                            updateReversionPosition(Case1Ticker.NAPA, -x);
                            updateReversionPosition(Case1Ticker.NAPB, -x);
                        }
                    }

                    if (((NAPAcurr < NAPBcurr) && (Math.abs(forecastNAP) > Math.abs(W_t1NAP)))
                            || ((NAPAcurr > NAPBcurr) && (Math.abs(forecastNAP) < Math.abs(W_t1NAP)))) {
                        if (tick % 2 == 0) {
                            order(Case1Ticker.NAPA, x);
                            order(Case1Ticker.NAPB, -x);
                            updateReversionPosition(Case1Ticker.NAPA, -x);
                            updateReversionPosition(Case1Ticker.NAPB, -x);
                        }
                    }

                    if (((YBERAcurr > YBERBcurr) && (Math.abs(forecastYBER) > Math.abs(W_t1YBER)))
                            || ((YBERAcurr < YBERBcurr) && (Math.abs(forecastYBER) < Math.abs(W_t1YBER)))) {
                        if (tick % 2 == 0) {
                            order(Case1Ticker.YBERA, -y);
                            order(Case1Ticker.YBERB, y);
                            updateReversionPosition(Case1Ticker.YBERA, -y);
                            updateReversionPosition(Case1Ticker.YBERB, y);
                        }
                    }

                    if (((YBERAcurr < YBERBcurr) && (Math.abs(forecastYBER) > Math.abs(W_t1YBER)))
                            || ((YBERAcurr > YBERBcurr) && (Math.abs(forecastYBER) < Math.abs(W_t1YBER)))) {
                        if (tick % 2 == 0) {
                            order(Case1Ticker.YBERA, y);
                            order(Case1Ticker.YBERB, -y);
                            updateReversionPosition(Case1Ticker.YBERA, y);
                            updateReversionPosition(Case1Ticker.YBERB, -y);
                        }
                    }
                }


                // Convergence Algorithm
                if ((recentTicksNAP.get(2) > recentTicksNAP.get(1) && recentTicksNAP.get(1) > recentTicksNAP.get(0) && recentTicksNAP.get(0) > 1.15 * computeWbar(W_tNAP))
                        && recentTicksNAP.get(3) > recentTicksNAP.get(4) && recentTicksNAP.get(4) > recentTicksNAP.get(5) && recentTicksNAP.get(5) > 1.15 * computeWbar(W_tNAP)) {
                    if (tick % 2 == 0) {
                        // Order shares of NAP
                        Case1Ticker higherPrice = higherPrice(NAPAcurr, NAPBcurr, Case1Ticker.NAPA, Case1Ticker.NAPB);
                        Case1Ticker lowerPrice = lowerPrice(NAPAcurr, NAPBcurr, Case1Ticker.NAPA, Case1Ticker.NAPB);
                        order(higherPrice, -1000);
                        order(lowerPrice, 1000);
                        updateConvergencePosition(higherPrice, lowerPrice);

                        // Order shares of YBER
                        higherPrice = higherPrice(YBERAcurr, YBERBcurr, Case1Ticker.YBERA, Case1Ticker.YBERB);
                        lowerPrice = lowerPrice(NAPAcurr, NAPBcurr, Case1Ticker.YBERA, Case1Ticker.YBERB);
                        order(higherPrice, -1000);
                        order(lowerPrice, 1000);
                        updateConvergencePosition(higherPrice, lowerPrice);
                    }
                }
                if (tick % 1 == 0)
                    clearConvergencePosition();
            }

        }
        // Keep track of the last tick
        previousNAPB = NAPBcurr;
        previousYBERB = YBERBcurr;

    }

    /**
     * Helper method to clear the position for all shares purchased from the convergence method
     */
    private void clearConvergencePosition() {

        if (convergenceBook.get(Case1Ticker.NAPA) != null) {
            if (W_tNAP.get(tick) < 1.05*computeWbar(W_tNAP) || convergenceBook.get(Case1Ticker.NAPA) / 1000 >= 600 - tick) {
                if (convergenceBook.get(Case1Ticker.NAPA) > 0) {
                    order(Case1Ticker.NAPA, -1 * Math.min(1000, convergenceBook.get(Case1Ticker.NAPA)));
                } else if (convergenceBook.get(Case1Ticker.NAPB) > 0) {
                    order(Case1Ticker.NAPB, -1 * Math.max(1000, convergenceBook.get(Case1Ticker.NAPB)));
                }
            }
        }

        if (convergenceBook.get(Case1Ticker.YBERA) != null) {
            if (W_tYBER.get(tick) < 1.05*computeWbar(W_t1NAP) || convergenceBook.get(Case1Ticker.YBERA) / 1000 >= 600 - tick) {
                if (convergenceBook.get(Case1Ticker.YBERA) > 0) {
                    order(Case1Ticker.YBERA, -1 * Math.min(1000, convergenceBook.get(Case1Ticker.YBERA)));
                } else if (convergenceBook.get(Case1Ticker.YBERB) > 0) {
                    order(Case1Ticker.YBERB, -1 * Math.max(1000, convergenceBook.get(Case1Ticker.YBERB)));
                }
            }
        }
    }

    /**
     * Shrink our position to avoid penalties
     */
    private void shrinkPosition() {
        if (getPosition(Case1Ticker.NAPA) > 0)
            order(Case1Ticker.NAPA, Math.min(-1000, getPosition(Case1Ticker.NAPA)));
        else if(getPosition(Case1Ticker.NAPA) < 0)
            order(Case1Ticker.NAPA, Math.min(1000, -1*getPosition(Case1Ticker.NAPA)));

        if (getPosition(Case1Ticker.NAPB) > 0)
            order(Case1Ticker.NAPB, Math.min(-1000, getPosition(Case1Ticker.NAPB)));
        else if (getPosition(Case1Ticker.NAPB) < 0)
            order(Case1Ticker.NAPB, Math.min(1000, -1*getPosition(Case1Ticker.NAPB)));

        if (getPosition(Case1Ticker.YBERA) > 0)
            order(Case1Ticker.YBERA, Math.min(-1000, getPosition(Case1Ticker.YBERA)));
        else if (getPosition(Case1Ticker.YBERA) < 0)
            order(Case1Ticker.YBERA, Math.min(1000, -1* getPosition(Case1Ticker.YBERA)));

        if (getPosition(Case1Ticker.YBERB) > 0)
            order(Case1Ticker.YBERB, Math.min(-1000, getPosition(Case1Ticker.YBERB)));
        else if (getPosition(Case1Ticker.YBERB) < 0)
            order(Case1Ticker.YBERB, Math.min(1000, -1*getPosition(Case1Ticker.YBERB)));
    }

    /**
     *
     * @param higherPrice - the corresponding stock with the higher price
     * @param lowerPrice - the correspond stock with the lower price
     */
    private void updateConvergencePosition(Case1Ticker higherPrice, Case1Ticker lowerPrice) {
        if (convergenceBook.get(higherPrice) != null && convergenceBook.get(lowerPrice) != null) {
            convergenceBook.put(higherPrice, convergenceBook.get(higherPrice) - 1000);
            convergenceBook.put(lowerPrice, convergenceBook.get(lowerPrice) + 1000);
        }
        else {
            convergenceBook.put(higherPrice, -1000);
            convergenceBook.put(lowerPrice, 1000);
        }
    }

    /**
     * Helper method to clear the position for all shares purchased from the reversion method
     */
    private void clearReversionPosition() {
        order(Case1Ticker.NAPA, -reversionBook.get(Case1Ticker.NAPA));
        order(Case1Ticker.NAPB, -reversionBook.get(Case1Ticker.NAPB));
        order(Case1Ticker.YBERA, -reversionBook.get(Case1Ticker.YBERA));
        order(Case1Ticker.YBERB, -reversionBook.get(Case1Ticker.YBERB));
    }

    /**
     * Keep track of the shares purchased using the mean reversion model
     * @param stock - NAPA \ NAPB | YBERA | YBERB
     * @param stocks - the number of shares
     */
    private void updateReversionPosition(Case1Ticker stock, Integer stocks) {
        if (reversionBook.get(stock) != null) {
            reversionBook.put(stock, reversionBook.get(stock) + stocks);
        }
        else
            reversionBook.put(stock,stocks);
    }

    /**
     * @param currentPriceA - The current price
     * @param currentPriceB - The current price
     * @param stockA - NAPA | YBERA
     * @param stockB - NAPB | YBERB corresponding to stockA
     * @return The stock that is higher in price
     */
    private Case1Ticker higherPrice(double currentPriceA, double currentPriceB, Case1Ticker stockA, Case1Ticker stockB) {
        return (currentPriceA > currentPriceB) ? stockA : stockB;
    }

    /**
     * @param currentPriceA - The current price
     * @param currentPriceB - The current price
     * @param stockA - NAPA | YBERA
     * @param stockB - NAPA | YBERA
     * @return The stock that is lower in price
     */
    private Case1Ticker lowerPrice(double currentPriceA, double currentPriceB, Case1Ticker stockA, Case1Ticker stockB) {
        return (currentPriceA < currentPriceB) ? stockA : stockB;
    }

    /**
     *
     * @param x - number of shares
     * @param y - number of shares
     * @return The risk associated with given number of x and y shares
     */
    private double calculateRisk(double x, double y) {

        // Calculate the risk corresponding to given shares of x and y using pre-calculated
        // Variance and Covariances from sample data
        double cov1A1B = 208.12277419873587;
        double cov2A2B = 325.20749095124995;
        double cov1A2B = 81.86116935885032;
        double cov1B2A = 83.73013485254526;
        double cov1B2B = 31.913755039213456;
        double cov1A2A = 215.00176380264622;

        double var1A = 539.6437224012117;
        double var1B = 80.79515272494945;
        double var2A = 845.3388041604453;
        double var2B = 125.51623283873849;

        double risk = Math.pow(x,2) * (var1A + var1B - 2*cov1A1B);
        risk += Math.pow(y,2) * (var2A + var2B - 2*cov2A2B);
        risk += 2*x*y* (cov1A2A - cov1A2B - cov1B2A + cov1B2B);
        return risk;
    }

    /**
     * Optimize x,y with f(x) = profit/risk
     * @param W_tNAP - Spread of NAP
     * @param W_t1NAP - Spread of NAP at time t+1
     * @param W_tYBER - Spread of YBER
     * @param W_t1YBER - Spread of YBER at time t+1
     */
    private void calculateShares(double W_tNAP, double W_t1NAP, double W_tYBER, double W_t1YBER) {

        // Compute optimal shares using nested for loop because optimization formula
        // Does not have a closed form solution
        x = 0;
        y = 0;
        double max = 0;
        for (int x = 100; x < 1000; x+=40) {
            for (int y = 500; y < 1000; y+=40) {
                double profit = calculateProfit(x, y, W_tNAP, W_t1NAP, W_tYBER, W_t1YBER);
                double risk = calculateRisk(x, y);
                if ((profit / risk) > max) {
                    max = (profit / risk);
                    this.x = x;
                    this.y = y;
                }
            }
        }
        log("x: " + x);
        log("y: " + y);
    }

    /**
     * @param x - number of shares
     * @param y -  number of shares
     * @param W_tNAP - Spread of NAP
     * @param W_t1NAP - Spread of NAP at time t+1
     * @param W_tYBER - Spread of YBER
     * @param W_t1YBER - Spread of YBER at time t+1
     * @return The associated profit given inputs
     */
    private double calculateProfit(double x, double y, double W_tNAP, double W_t1NAP, double W_tYBER, double W_t1YBER) {

        return Math.abs(x) * (Math.abs(Math.abs(W_tNAP) - Math.abs(W_t1NAP))) +
                Math.abs(y) * (Math.abs(Math.abs(W_tYBER) - Math.abs(W_t1YBER)));
    }

    /**
     * Compute the Standard Deviation from data of the Spread of a stock of every tick
     * @param W_t - an ArrayList of the spread of a stock at every tick
     * @return
     */
    private double computeSD(ArrayList<Double> W_t) {

        // Average
        double sum = 0;
        for (int i = 0; i < 50-2; i++) {
            sum += W_t.get(i);
        }
        double avg = sum/50;

        // Variance
        double sum_sd = 0;
        for (int i = 0; i < 50-2; i++) {
            sum_sd += Math.pow(W_t.get(i)-avg,2);
        }

        // Return Standard Deviation
        return Math.sqrt(sum_sd/50);
    }

    /**
     *
     * @param W_t - an ArrayList of the spread of a stock at every tick
     * @param W_t1 - an ArrayList of the difference in the spread of a tick between tick t and t+1
     * @return
     */
    private double computeM(ArrayList<Double> W_t, ArrayList<Double> W_t1) {
        // -m
        // Compute average for Wbar
        double sum = 0;
        for (int i = 0; i < 50-2; i++) {
            sum += W_t.get(i);
        }
        double avg = sum/50;    //W bar

        // W_tbar
        double avg_t = computeW_tBar(W_t, W_t1);
        log("avg_t" + avg_t);
        // (W_1-Wbar)^2
        double devSQ = 0;
        for (int i = 0; i < 50-2; i++) {
            devSQ += Math.pow(W_t1.get(i+1)-W_t.get(i),2);
        }

        double dev = 0;
        double dev_1 = 0;
        double m = 0;
        for (int i = 0; i < 50-2; i++) {
            dev = W_t.get(i)-avg;                   // W_i-Wbar
            dev_1 = W_t.get(i+1)- W_t.get(i);       //W_i+1-W_i
            m += dev*(dev_1-avg_t);
        }
        m = m/devSQ;
        return m;

    }

    /**
     * Compute the expected value of the spread of a stock
     * @param W_t - an ArrayList of the spread of a stock at every tick
     * @param W_t1 - an ArrayList of the difference in the spread of a tick between tick t and t+1
     * @return
     */
    private double computeW_tBar(ArrayList<Double> W_t, ArrayList<Double> W_t1) {
        double sum_1 = 0;
        for (int i = 0; i < W_t1.size()-2; i++) {
            sum_1 += W_t1.get(i+1)-W_t.get(i);
        }
        double avg_t = sum_1/W_t1.size();    // W_t+1 - W_t/50
        return avg_t;
    }

    /**
     *
     * @param stock the spread of the stock at every tick
     * @return the expected value of the spread
     */
    private double computeWbar(ArrayList<Double> stock) {
        double sum = 0;
        for (int i = 0; i < stock.size(); i ++) {
            sum += stock.get(i);
        }
        return sum/stock.size();
    }

}
