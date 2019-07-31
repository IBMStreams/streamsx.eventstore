package com.ibm.streamsx.eventstore.i18n;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * fetch translated strings from the correct properties file
 * according to the current locale 
 */
public class Messages
{
	// the messages* files are searched in the directory of this class
	private static final String BUNDLE_NAME = "com.ibm.streamsx.eventstore.i18n.messages.messages"; //$NON-NLS-1$#

	// load the bundle based on the current locale
	private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

	private Messages()
	{
	}

    public static String getString(String key, Object... params  ) {
        try {
            return MessageFormat.format(RESOURCE_BUNDLE.getString(key), params);
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }
}
