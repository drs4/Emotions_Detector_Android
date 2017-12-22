package com.s4u.emotionsdetector.http;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The main different from the PrefUtil is the ability to set certain file reference
 * Created by Saad on 7/3/2016.
 *
 */

public class PreferenceCache {

    SharedPreferences sharedPreferences;


    /**
     * @param context clear enough
     * @param name the name of the file which the preferences will be saved to
     */
    public PreferenceCache(Context context,String name) {
        sharedPreferences = context.getSharedPreferences(name,Context.MODE_PRIVATE);
    }

    public void setStringPreference(String key,String value)
    {
        sharedPreferences.edit().putString(key,value).apply();
    }
    public void setIntegerPreference(String key,int value)
    {
        sharedPreferences.edit().putInt(key, value).apply();
    }
    public String getStringPreference(String key)
    {
        return sharedPreferences.getString(key,"");
    }

    public int getIntegerPreference(String key)
    {
        return sharedPreferences.getInt(key, 0);
    }

    public void deletePreference(String key)
    {
        sharedPreferences.edit().remove(key).apply();
    }

    public void clearPreference()
    {
        sharedPreferences.edit().clear().apply();
    }

}
