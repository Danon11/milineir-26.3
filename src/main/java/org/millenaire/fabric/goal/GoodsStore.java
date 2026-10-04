package org.millenaire.fabric.goal;

/** Goods held by a building, counted by itemlist alias. */
public interface GoodsStore {
    int count(String good);
    /** Removes up to {@code amount}; returns how many were removed. */
    int remove(String good, int amount);
    /** Adds up to {@code amount}; returns how many fit. */
    int add(String good, int amount);
    /** How many could be added without losing items. */
    int space(String good);
}
