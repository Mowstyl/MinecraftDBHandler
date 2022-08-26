package com.clanjhoo.dbhandler.samples;

import com.clanjhoo.dbhandler.annotations.Entity;

import java.util.UUID;

@Entity
public class SampleEconomyPlayer {
    private UUID playerId;
    private long currency;
}
