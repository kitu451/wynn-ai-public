package net.natga999.wynn_ai;

import net.fabricmc.api.ModInitializer;

public class Wynn_ai implements ModInitializer {

    @Override
    public void onInitialize() {
        // Initialize client-side rendering
        new TestRender().onInitializeClient();
    }
}
