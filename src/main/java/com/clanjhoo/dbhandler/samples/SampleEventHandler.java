package com.clanjhoo.dbhandler.samples;

import com.clanjhoo.dbhandler.DBHandler;
import com.clanjhoo.dbhandler.data.DBObjectManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;


public class SampleEventHandler implements Listener {

    private final DBObjectManager<SampleEntity> manager;


    public SampleEventHandler() {
        manager = DBHandler.getInstance().getMyEntityManager();
    }

    // The event is not modifiable nor cancellable, you can only check its results
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDataLoad(SampleLoadEvent ev) {
        SampleEntity.entities.add(ev.getData());
    }

    public void onPlayerEnter(PlayerJoinEvent event) {
        // We schedule the load of the data
        manager.loadData(event.getPlayer().getUniqueId());
    }

    public void onPlayerLeave(PlayerQuitEvent event) {
        // We save player data on leave
        manager.save(event.getPlayer().getUniqueId());
    }
}
