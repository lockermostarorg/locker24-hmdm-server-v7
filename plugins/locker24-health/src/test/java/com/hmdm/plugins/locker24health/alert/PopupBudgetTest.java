package com.hmdm.plugins.locker24health.alert;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PopupBudgetTest {

    private static final long T = 1_763_290_000_000L;
    private static final long MIN = 60_000L;

    @Test
    public void freshBudgetIsMax() {
        assertEquals(5, new PopupBudget(5, 10 * MIN).available(1, T));
    }

    @Test
    public void spentPopupsBlockForTheWindow() {
        PopupBudget b = new PopupBudget(5, 10 * MIN);
        b.spend(1, T, 5);
        assertEquals(0, b.available(1, T + MIN));
        assertEquals(0, b.available(1, T + 10 * MIN - 1));
        assertEquals(5, b.available(1, T + 10 * MIN));
    }

    @Test
    public void rollsOffInPieces() {
        PopupBudget b = new PopupBudget(5, 10 * MIN);
        b.spend(1, T, 3);
        b.spend(1, T + 5 * MIN, 2);
        assertEquals(0, b.available(1, T + 6 * MIN));
        assertEquals(3, b.available(1, T + 10 * MIN));
        assertEquals(5, b.available(1, T + 15 * MIN));
    }

    @Test
    public void customersAreIndependent() {
        PopupBudget b = new PopupBudget(5, 10 * MIN);
        b.spend(1, T, 5);
        assertEquals(5, b.available(2, T));
    }

    @Test
    public void neverNegativeAndIgnoresZero() {
        PopupBudget b = new PopupBudget(5, 10 * MIN);
        b.spend(1, T, 0);
        b.spend(1, T, -3);
        assertEquals(5, b.available(1, T));
        b.spend(1, T, 9);
        assertEquals(0, b.available(1, T));
    }
}
