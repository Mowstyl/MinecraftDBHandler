package com.clanjhoo.dbhandler;

import com.clanjhoo.dbhandler.data.DBObjectManager;
import com.clanjhoo.dbhandler.data.SaveOperation;
import com.clanjhoo.dbhandler.data.StorageType;
import com.clanjhoo.dbhandler.samples.SampleEntity;
import com.clanjhoo.dbhandler.samples.SampleEventHandler;
import com.clanjhoo.dbhandler.samples.SampleLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.logging.Level;


/**
 * Sample plugin using DBHandler
 */
public final class DBHandler extends JavaPlugin {

    private DBObjectManager<SampleEntity> myEntityManager;


    @Override
    public void onEnable() {
        // Plugin startup logic
        try {
            // DBObjectManager creation
            myEntityManager = new DBObjectManager<>(
                    SampleEntity.class,
                    this,
                    StorageType.JSON,
                    SampleLoadEvent::new,
                    // Only store items in the DB if they aren't the default ones
                    (se) -> Math.abs(se.bolognesa - 3.2) < .0000001 && Math.abs(se.ravioliRavioli) < .0000001,
                    // 5 minutes inactive time
                    5 * 60 * 1000,
                    "store");
        }
        catch (IOException ex) {
            getLogger().log(Level.SEVERE, "There was an error while creating the table/folder");
            myEntityManager = null;
            setEnabled(false);
            return;
        }

        // Only needed for foreign keys. Call it after all involved managers have been instantiated.
        myEntityManager.initialize();

        // Register the event listener
        Bukkit.getPluginManager().registerEvents(new SampleEventHandler(), this);

        //
    }


    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (myEntityManager != null) {
            myEntityManager.stopRunningTasks();
            myEntityManager.saveAllSync(SaveOperation.SAVE_ALL);
        }
    }


    /**
     * Returns the sample entity manager that handles the data
     * @return the entity manager
     */
    @Nullable
    public DBObjectManager<SampleEntity> getMyEntityManager() {
        return myEntityManager;
    }


    /**
     * Returns the one and only instance of the DBHandler that Bukkit has created
     * @return a DBHandler instance
     */
    public static DBHandler getInstance() {
        return (DBHandler) Bukkit.getPluginManager().getPlugin("DBHandler");
    }
}
