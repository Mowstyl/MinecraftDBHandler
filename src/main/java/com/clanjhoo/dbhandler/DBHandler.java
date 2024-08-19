package com.clanjhoo.dbhandler;

import com.clanjhoo.dbhandler.data.DBObjectManager;
import com.clanjhoo.dbhandler.data.StorageType;
import com.clanjhoo.dbhandler.samples.SampleEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Level;


public final class DBHandler extends JavaPlugin {
    DBObjectManager<SampleEntity> myEntityManager;

    @Override
    public void onLoad() {
        // Plugin startup logic
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        try {
            myEntityManager = new DBObjectManager<>(SampleEntity.class, null, this, null, StorageType.JSON, "store");
            myEntityManager.initialize();
            // this.getLogger().log(Level.INFO, myEntityManager.getTableData().getCreateString(null));
            myEntityManager.getFutureData(UUID.fromString("c38ee158-c001-49b6-91ef-af447b11d742")).thenAccept((me) -> {
                this.getLogger().log(Level.INFO, me.id + " " + me.bolognesa + " " + me.extra);
            });/*.exceptionally((ex) -> {
                this.getLogger().log(Level.WARNING, "WA");
            });*/
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        myEntityManager.saveAndRemoveAllSync();
    }
}
