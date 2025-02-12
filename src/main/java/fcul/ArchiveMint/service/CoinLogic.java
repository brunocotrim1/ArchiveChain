package fcul.ArchiveMint.service;


import fcul.ArchiveMint.model.Coin;
import fcul.ArchiveMint.model.transactions.CurrencyTransaction;
import fcul.ArchiveMint.utils.CryptoUtils;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CoinLogic implements Serializable {
    private ConcurrentHashMap<String, List<Coin>> coinMap;
    private static final int redeemableCoinValue = 1000;
    private BigInteger idGenerator = BigInteger.ZERO;

    public CoinLogic() {
        coinMap = new ConcurrentHashMap<>();
    }

    public Coin createCoin(String address, BigInteger value) {
        Coin coin = new Coin(address, value, idGenerator);
        idGenerator = idGenerator.add(BigInteger.ONE);
        if (!coinMap.containsKey(address)) {
            List<Coin> coins = new ArrayList<>();
            coins.add(coin);
            coinMap.put(address, coins);
        } else {
            coinMap.get(address).add(coin);
        }
        return coin;
    }

    public boolean spendCoin(String senderAddress, String receiverAddress, List<BigInteger> coinIds, BigInteger amount) {

        if(!validCoinUsage(senderAddress, receiverAddress, coinIds, amount)){
            return false;
        }


        BigInteger valueFromAllCoins = valueFromAllOwnerCoins(senderAddress, coinIds);
        consumeCoinsOwner(senderAddress, coinIds);
        BigInteger change = valueFromAllCoins.subtract(amount);
        System.out.println("SPENDIND COINS");
        if(change.compareTo(BigInteger.ZERO) == 0){
            createCoin(receiverAddress, amount);
            return true;
        }else{
            createCoin(receiverAddress, amount);
            createCoin(senderAddress, change);
            return true;
        }
    }

    public boolean validCoinUsage(String senderAddress, String receiverAddress, List<BigInteger> coinIds, BigInteger amount){
        if (!coinMap.containsKey(senderAddress) || coinIds == null || coinIds.isEmpty()) {
            return false;
        }

        BigInteger valueFromAllCoins = valueFromAllOwnerCoins(senderAddress, coinIds);
        if (valueFromAllCoins.compareTo(BigInteger.valueOf(-1)) == 0 || amount.compareTo(BigInteger.ZERO) <= 0) {
            System.out.println("Here1");
            return false;
        }
        if (valueFromAllCoins.compareTo(amount) < 0 || senderAddress.equals(receiverAddress)) {
            System.out.println("Here2");
            return false;
        }
        return true;
    }


    private void consumeCoinsOwner(String address, List<BigInteger> coins){
        List<Coin> ownerCoins = coinMap.get(address);
        try {
            Iterator<Coin> iterator = ownerCoins.iterator();
            while (iterator.hasNext()) {
                Coin coin = iterator.next();
                if (coins.contains(coin.getId())) {
                    iterator.remove();
                }
            }
        } catch (Exception e) {
            System.out.println("Error consuming coins");
            e.printStackTrace();
        }
    }

    private BigInteger valueFromAllOwnerCoins(String address, List<BigInteger> coinIds) {
        BigInteger value = BigInteger.ZERO;
        List<BigInteger> ownerCoins = coinMap.get(address).stream().map(Coin::getId).toList();

        for(BigInteger coinId : coinIds){
            if (!ownerCoins.contains(coinId)) {
                return BigInteger.valueOf(-1);
            }
            for(Coin coin : coinMap.get(address)){
                if(coin.getId().equals(coinId)){
                    value = value.add(coin.getValue());
                }
            }

        }

        return value;
    }

    public boolean validTransaction(CurrencyTransaction transaction) {
        try {
            byte[] signature = Hex.decodeHex(transaction.getSignature());
            byte[] senderPk = Hex.decodeHex(transaction.getSenderPk());

            if (!CryptoUtils.ecdsaVerify(signature, Hex.decodeHex(transaction.getTransactionId()), senderPk)) {
                System.out.println("Invalid signature Transaction");
                return false;
            }
            String address = CryptoUtils.getWalletAddress(transaction.getSenderPk());
            if (!address.equals(transaction.getSenderAddress())) {
                System.out.println("Cant send to himself");
                return false;
            }

            if(!validCoinUsage(transaction.getSenderAddress(), transaction.getReceiverAddress(), transaction.getCoins(),
                    transaction.getAmount())){
                System.out.println("Invalid amount Transaction");
                return false;
            }
            return true;
        } catch (DecoderException e) {
            return false;
        }
    }

    public List<Coin> getCoins(String address){
        if(!coinMap.containsKey(address)){
            return new ArrayList<>();
        }
        return coinMap.get(address);
    }


    public CoinLogic clone() {
        CoinLogic clone = new CoinLogic();
        // Deep copy coinMap
        clone.coinMap = new ConcurrentHashMap<>();
        for (String address : this.coinMap.keySet()) {
            List<Coin> originalList = this.coinMap.get(address);
            List<Coin> copiedList = new ArrayList<>();
            for (Coin coin : originalList) {
                copiedList.add(new Coin(coin.getOwner(), coin.getValue(), coin.getId())); // Assuming a copy constructor in Coin class
            }
            clone.coinMap.put(address, copiedList);
        }
        clone.idGenerator = new BigInteger(idGenerator.toString());
        return clone;
    }
}