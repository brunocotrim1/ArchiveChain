package fcul.ArchiveMint.service;


import fcul.ArchiveMintUtils.Model.transactions.CurrencyTransaction;
import fcul.ArchiveMintUtils.Model.transactions.Transaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CachedModifications {
    private Map<String, List<BigInteger>> coins = new HashMap<>();

    public void addCoin(String address, BigInteger coin) {
        if(coins == null){
            return;
        }
        if(coins.containsKey(address)){
            coins.get(address).add(coin);
        } else {
            List<BigInteger> coinList = new ArrayList<>();
            coinList.add(coin);
            coins.put(address, coinList);
        }
    }
    public boolean coindDoubleSpend(String address, BigInteger coinId){
        if(coins.containsKey(address)){
            return coins.get(address).contains(coinId);
        }
        return false;
    }

    public boolean verifyDoubleSpend(Transaction transaction){
        switch (transaction.getType()) {
            case CURRENCY_TRANSACTION:
                CurrencyTransaction currencyTransaction = (CurrencyTransaction) transaction;
                for(BigInteger coin : currencyTransaction.getCoins()){
                    if(coin == null || coindDoubleSpend(currencyTransaction.getSenderAddress(), coin)){
                        throw new RuntimeException("Double spend detected");
                    }
                    addCoin(currencyTransaction.getSenderAddress(), coin);
                }
                break;
            default:
                throw new RuntimeException("Invalid transaction type");
        }
        return true;
    }
}
