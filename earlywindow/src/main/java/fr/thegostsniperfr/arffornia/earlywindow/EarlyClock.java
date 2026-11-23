package fr.thegostsniperfr.arffornia.earlywindow;

/** Animation time in seconds. `-Darffornia.earlywindow.testClock=<s>` freezes it so screenshots are stable. */
final class EarlyClock {
    static final String TEST_CLOCK = "arffornia.earlywindow.testClock";
    private final long start = System.nanoTime();

    float seconds() {
        String frozen = System.getProperty(TEST_CLOCK);
        if (frozen != null) {
            return Float.parseFloat(frozen);
        }
        return (System.nanoTime() - start) / 1e9f;
    }
}
