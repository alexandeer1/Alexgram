package com.exteragram.messenger.plugins.models;

/**
 * Minimal compatibility stub of exteraGram's {@code CustomSetting} so plugins that import it and
 * reference {@code CustomSetting.Factory} load. Full custom-setting rendering relies on the
 * class-proxy (DexMaker) layer, which is not implemented in ZaStoGram — subclassing the Factory
 * therefore does not render, but the import and class reference resolve.
 */
public class CustomSetting {

    public Object object;

    public static class Factory {
        // Intentionally empty stub (see class javadoc).
    }
}
