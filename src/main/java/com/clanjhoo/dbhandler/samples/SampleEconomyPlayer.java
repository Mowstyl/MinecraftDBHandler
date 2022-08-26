package com.clanjhoo.dbhandler.samples;

import com.clanjhoo.dbhandler.annotations.Entity;
import com.clanjhoo.dbhandler.annotations.PrimaryKey;

import java.util.UUID;

@Entity
public class SampleEconomyPlayer {
    @PrimaryKey
    private UUID playerId;
    private long currency;
}
