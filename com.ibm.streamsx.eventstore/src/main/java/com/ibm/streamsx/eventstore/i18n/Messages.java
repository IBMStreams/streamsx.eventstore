package com.ibm.streamsx.eventstore.i18n;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Locale;

/**
 * fetch translated strings from the correct properties file
 * according to the current locale 
 */
public class Messages
{
	// the messages* files are searched in the directory of this class
	private static final String BUNDLE_NAME = "com.ibm.streamsx.eventstore.i18n.messages.messages"; //$NON-NLS-1$#

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle (BUNDLE_NAME);
    private static final ResourceBundle FALLBACK_RESOURCE_BUNDLE = ResourceBundle.getBundle (BUNDLE_NAME, new Locale ("en", "US"));

    private Messages() {
    }

    public static String getString(String key) {
        try {
            return getRawMsg (key);
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }

    public static String getString (String key, Object... args) {
        try {
            String msg = getRawMsg (key);
            if (args == null) return msg;
            return MessageFormat.format (msg, args);
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }

    private static String getRawMsg (String key) throws MissingResourceException {
        try {
            return RESOURCE_BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return FALLBACK_RESOURCE_BUNDLE.getString(key);
        }
    }
}
