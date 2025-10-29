package fr.elias.ultimateChristmas.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WeightedRandomPicker<T> {

    private static final Random RAND = new Random();
    private final List<T> entries = new ArrayList<>();
    private final List<Integer> weights = new ArrayList<>();
    private int total = 0;

    public void add(T obj, int weight) {
        if (weight <= 0) return;
        entries.add(obj);
        weights.add(weight);
        total += weight;
    }

    public T pick() {
        if (entries.isEmpty() || total <= 0) return null;
        int roll = RAND.nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < entries.size(); i++) {
            cumulative += weights.get(i);
            if (roll < cumulative) {
                return entries.get(i);
            }
        }
        return entries.get(entries.size() - 1);
    }
}
