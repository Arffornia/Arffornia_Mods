package fr.thegostsniperfr.arffornia.shop.internal;

import java.util.List;

/**
 * Represents a pending reward fetched from the database.
 * Using a record for simple, immutable data holding.
 *
 * @param id The unique ID of the reward in the database.
 * @param commands The list of commands to execute for this reward.
 */
public record PendingReward(int id, List<String> commands) {}