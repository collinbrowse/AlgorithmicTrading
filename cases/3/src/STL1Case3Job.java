import org.optionscity.uchicago.job.case3.AbstractCase3Job;

import com.sun.corba.se.spi.ior.MakeImmutable;
import org.optionscity.uchicago.common.case3.*;
import org.optionscity.uchicago.job.case3.AbstractCase3Job;

import java.util.ArrayList;
import java.util.concurrent.Callable;

public class STL1Case3Job extends AbstractCase3Job {

    double[] bidPercentage = {0.95, 0.85, 0.80, 0.77, 0.75, 0.735, 0.72, 0.7};
    double[] askPercentage = {1.05, 1.15, 1.2, 1.23, 1.25, 1.265, 1.275, 1.3};

    double bPercentage;
    double aPercentage;
    double PnL;
    ArrayList<Double> PnL_list = new ArrayList<Double>();

    boolean NeedtoIncreaseB = false;
    boolean NeedToDecreaseB = false;
    boolean NeedtoIncreaseA = false;
    boolean NeedtoDecreaseA = false;
    int curr = 0;

    @Override
    public void onTick() {

        PnL = getPnL();
        PnL_list.add(PnL);

        bPercentage = bidPercentage[curr];
        aPercentage = askPercentage[curr];

        // After 50 ticks, start to determine b/a percentage
        if (PnL_list.size() > 50){
            update_boolean();

            bPercentage = calculate_bid();
            aPercentage = calculate_ask();
        }

        // Get total delta, vega and current underlying price
        double delta = getTotalDelta();
        double vega = getTotalVega();
        double price = getPrice(Case3Ticker.UCHIX);

        // Different cases
        boolean delta_excced = Math.abs(delta) >= 850;
        boolean vega_exceed = Math.abs(vega) >= 400;
        boolean both_exceed = delta_excced && vega_exceed;

        // Find in the money & at the money & other
        ArrayList<Case3Option> in_the_money = new ArrayList<Case3Option>();
        ArrayList<Case3Option> at_the_money = new ArrayList<Case3Option>();
        ArrayList<Case3Option> other = new ArrayList<Case3Option>();

        for (Case3Option option : Case3Option.values()) {
            if ((option.type == OptionType.CALL && option.strike < price) || (option.type == OptionType.PUT && option.strike > price)) {
                in_the_money.add(option);
            } else if ((int)(option.strike)==price){
                at_the_money.add(option);
            }else{
                other.add(option);
            }
        }

        // Deal with delta
        if (delta_excced && !vega_exceed) {

            // Make market
            if (delta <= -850) {
                for (Case3Option option : in_the_money) {
                    Market market = new Market(getGreeks(option).theoreticalPrice * askPercentage[1], getGreeks(option).theoreticalPrice * askPercentage[3]);
                    makeMarket(option, market);
                }
            } else if (delta >= 850) {
                for (Case3Option option : in_the_money) {
                    Market market = new Market(getGreeks(option).theoreticalPrice * bidPercentage[3], getGreeks(option).theoreticalPrice * bidPercentage[1]);
                    makeMarket(option, market);
                }
            }

            // Add other and at the money
            other.addAll(at_the_money);

            for (Case3Option option : other) {
                Market market = new Market(getGreeks(option).theoreticalPrice * bPercentage, getGreeks(option).theoreticalPrice * aPercentage);
                makeMarket(option, market);
            }

        }
        // Deal with vega
        else if (vega_exceed && !delta_excced){

            // Make market
            if (vega <= -400){
                for (Case3Option option : at_the_money) {
                    Market market = new Market(getGreeks(option).theoreticalPrice * askPercentage[1], getGreeks(option).theoreticalPrice * askPercentage[3]);
                    makeMarket(option, market);
                }
            }else if(vega >= 400){
                for (Case3Option option : at_the_money) {
                    Market market = new Market(getGreeks(option).theoreticalPrice * bidPercentage[3], getGreeks(option).theoreticalPrice * bidPercentage[1]);
                    makeMarket(option, market);
                }
            }

            // Add in the money and other together
            other.addAll(in_the_money);

            for (Case3Option option : other) {
                Market market = new Market(getGreeks(option).theoreticalPrice * bPercentage, getGreeks(option).theoreticalPrice * aPercentage);
                makeMarket(option, market);
            }

        }
        // Deal with vega & delta
        // Haven't finished yet. Notice there are many different cases
        else if (both_exceed){

            // Make market, deal with delta
            if (delta <= -850) {
                for (Case3Option option : in_the_money) {
                    Market market = new Market(getGreeks(option).theoreticalPrice * askPercentage[1], getGreeks(option).theoreticalPrice * askPercentage[3]);
                    makeMarket(option, market);
                }
            } else if (delta >= 850) {
                for (Case3Option option : in_the_money) {
                    Market market = new Market(getGreeks(option).theoreticalPrice * bidPercentage[3], getGreeks(option).theoreticalPrice * bidPercentage[1]);
                    makeMarket(option, market);
                }
            }

            // Make market, deal with vega
            if (vega <= -400){
                for (Case3Option option : at_the_money) {
                    Market market = new Market(getGreeks(option).theoreticalPrice * askPercentage[1], getGreeks(option).theoreticalPrice * askPercentage[3]);
                    makeMarket(option, market);
                }
            }else if(vega >= 400){
                for (Case3Option option : at_the_money) {
                    Market market = new Market(getGreeks(option).theoreticalPrice * bidPercentage[3], getGreeks(option).theoreticalPrice * bidPercentage[1]);
                    makeMarket(option, market);
                }
            }

            for (Case3Option option : other) {
                Market market = new Market(getGreeks(option).theoreticalPrice * bPercentage, getGreeks(option).theoreticalPrice * aPercentage);
                makeMarket(option, market);
            }

        }else {
            for (Case3Option option : Case3Option.values()) {
                Market market = new Market(getGreeks(option).theoreticalPrice * bPercentage, getGreeks(option).theoreticalPrice * aPercentage);
                makeMarket(option, market);
            }
        }

    }

    @Override
    public void onVegaLimitBreached() {
        double vega = getTotalVega();

        // Get options that have the most vega
        double price = getPrice(Case3Ticker.UCHIX);
        ArrayList<Case3Option> vega_option = new ArrayList<Case3Option>();

        for (Case3Option option : Case3Option.values()){
            if (Math.abs(option.strike - price) <= 3.5){
                vega_option.add(option);
            }
        }

        // Is it will be a issue if I make market for one option several times on one tick???
        if (vega >= 580){
            for (Case3Option option : vega_option){
                Market market = new Market(getGreeks(option).theoreticalPrice * bidPercentage[5], getGreeks(option).theoreticalPrice * bidPercentage[3]);
                makeMarket(option, market);
            }
        }

        if (vega <= -580){
            for (Case3Option option : vega_option){
                Market market = new Market(getGreeks(option).theoreticalPrice * askPercentage[3], getGreeks(option).theoreticalPrice * askPercentage[5]);
                makeMarket(option, market);
            }
        }

    }


    @Override
    public void onDeltaLimitBreached() {

        double delta = getTotalDelta();

        if (delta >= 1100) {
            order(Case3Ticker.UCHIX, (int) (800 - delta) / 100);
        }
        if (delta <= -1100) {
            order(Case3Ticker.UCHIX, (int) (-800 - delta) / 100);
        }

    }

    public double calculate_bid(){

        if (NeedtoIncreaseB && curr != 7 && !NeedToDecreaseB){
            curr++;
        }

        if (NeedToDecreaseB && curr != 0 && !NeedtoIncreaseB){
            curr--;
        }

        return bidPercentage[curr];
    }

    public double calculate_ask(){
        if (NeedtoIncreaseA && curr != 7 && !NeedtoDecreaseA){
            curr++;
        }

        if (NeedtoDecreaseA && curr != 0 && !NeedtoIncreaseA){
            curr--;
        }

        return askPercentage[curr];
    }

    // Set boolean values
    public void update_boolean(){

        boolean temp = ((PnL_list.get(PnL_list.size() - 3) - PnL_list.get(PnL_list.size() - 2))/(PnL_list.get(PnL_list.size() - 3))) < 0;
        boolean temp2 = ((PnL_list.get(PnL_list.size() - 4) - PnL_list.get(PnL_list.size() - 3))/(PnL_list.get(PnL_list.size() - 4))) < 0;
        boolean temp1 = ((PnL_list.get(PnL_list.size() - 5) - PnL_list.get(PnL_list.size() - 4))/(PnL_list.get(PnL_list.size() - 4))) < 0;

        boolean temp3 = Math.abs(PnL_list.get(PnL_list.size() - 3) - PnL_list.get(PnL_list.size() - 2))
                < Math.abs(PnL_list.get(PnL_list.size() - 4) - PnL_list.get(PnL_list.size() - 3));
        boolean temp4 = Math.abs(PnL_list.get(PnL_list.size() - 5) - PnL_list.get(PnL_list.size() - 4))
                < Math.abs(PnL_list.get(PnL_list.size() - 6) - PnL_list.get(PnL_list.size() - 5));
        boolean temp5 = Math.abs(PnL_list.get(PnL_list.size() - 7) - PnL_list.get(PnL_list.size() - 6))
                < Math.abs(PnL_list.get(PnL_list.size() - 8) - PnL_list.get(PnL_list.size() - 7));

        if (temp && temp2 && temp1){
            NeedToDecreaseB = true;
            NeedtoIncreaseA = true;
        }

        if (temp3 && temp4 && temp5){
            NeedtoIncreaseB = true;
            NeedtoDecreaseA = true;
        }

    }

}