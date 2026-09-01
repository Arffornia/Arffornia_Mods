package fr.thegostsniperfr.arffornia.lootbox;

import java.util.List;

public class LootBoxData {
    public String name;
    public List<String> hologram;
    public String key_item;
    public List<Reward> rewards;

    public static class Reward {
        public String item;
        public int amount;
        public double chance;
    }
}