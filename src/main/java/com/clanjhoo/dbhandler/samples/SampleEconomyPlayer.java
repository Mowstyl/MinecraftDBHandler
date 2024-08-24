package com.clanjhoo.dbhandler.samples;

import com.clanjhoo.dbhandler.annotations.Entity;
import com.clanjhoo.dbhandler.annotations.PrimaryKey;

import java.util.UUID;

/**
 * A sample class that illustrates how to store the currency a player has
 */
@Entity
public class SampleEconomyPlayer {
    @PrimaryKey
    private UUID playerId;
    private long currency;
}
