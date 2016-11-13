MultiProcessSharedPreferencesProvider
=====================================

A multiprocess SharedPreferences implementation backed by a ContentProvider.

Since M, Context.MODE_MULTI_PROCESS was deprecated because it behaves different
depending on the version and device. This class provides a way to use
SharedPreferences across different processes in safe way.

### How to use

1.- Define the MultiProcessSharedPreferencesProvider class as a provider in
your manifest

```xml
<provider
    android:name="com.ruesga.preferences.MultiProcessSharedPreferencesProvider"
    android:authorities="com.android.providers"
    android:exported="false"/>
```

2.- And use it in the same way you used the SharedPreferences one

```java
MultiProcessSharedPreferencesProvider.MultiProcessSharedPreferences prefs =
        MultiProcessSharedPreferencesProvider.getDefaultSharedPreferences(
                getContext());
SharedPreferences.Editor editor = prefs.edit();
editor.putString("test", "value");
editor.apply();
```

Even you can register a listener to listen to prefs changing as you do
with your SharedPreferences

```java
MultiProcessSharedPreferencesProvider prefs =
    MultiProcessSharedPreferencesProvider.getDefaultSharedPreferences(
        ctx.getApplicationContext());
prefs.registerOnSharedPreferenceChangeListener(...);
```


Copyright © 2016 Jorge Ruesga
