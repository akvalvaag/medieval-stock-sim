package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PriceHistory {

    private static final int CAPACITY = 50;
    private final Map<String, Deque<Double>> buffers = new HashMap<>();

    public PriceHistory(GoodsCatalogue catalogue) {
        for (Good good : catalogue.getGoods()) {
            Deque<Double> buf = new ArrayDeque<>(CAPACITY);
            for (int i = 0; i < CAPACITY; i++) buf.addLast(good.getBasePrice());
            buffers.put(good.getName(), buf);
        }
    }

    public synchronized void append(String goodName, double price) {
        Deque<Double> buf = buffers.computeIfAbsent(goodName, k -> new ArrayDeque<>(CAPACITY));
        if (buf.size() >= CAPACITY) buf.pollFirst();
        buf.addLast(price);
    }

    public synchronized List<Double> getHistory(String goodName) {
        return new ArrayList<>(buffers.getOrDefault(goodName, new ArrayDeque<>()));
    }

    public synchronized Map<String, List<Double>> getAllHistory() {
        Map<String, List<Double>> result = new HashMap<>();
        buffers.forEach((k, v) -> result.put(k, new ArrayList<>(v)));
        return result;
    }
}
