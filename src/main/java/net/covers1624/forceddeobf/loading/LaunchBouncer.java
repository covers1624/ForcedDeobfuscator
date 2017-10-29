/*
 * (C) 2017 covers1624
 * All Rights Reserved
 */
package net.covers1624.forceddeobf.loading;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bouncer class to avoid class loading.
 *
 * Created by covers1624 on 29/10/2017.
 */
public class LaunchBouncer {

    private static Logger logger = LogManager.getLogger("ForcedDeobf-PreLaunch");
    public static String bounceClass = "net.minecraft.client.main.Main";

    public static void main(String[] args) throws Exception {
        TransformerSorter.doStuff();
        //bounce
        logger.info("Launching wrapped wrapped minecraft {{}}", bounceClass);
        Class.forName(bounceClass).getDeclaredMethod("main", String[].class).invoke(null, new Object[] { args });
    }

}
