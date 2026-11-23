package fr.thegostsniperfr.arffornia.client.screen.title;

/** Two frames of a looping clip and how far it is from the first to the second (0..1). */
record LoopFrames(int current, int next, float mix) {
    /** Frames at {@code seconds}: the last frame blends into the first, so the loop has no seam. */
    static LoopFrames at(float seconds, float fps, int count) {
        double position = seconds * (double) fps;
        int base = (int) Math.floor(position);
        int current = Math.floorMod(base, count);
        return new LoopFrames(current, (current + 1) % count, (float) (position - base));
    }
}
