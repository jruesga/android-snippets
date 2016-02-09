/*
 * Copyright (C) 2016 Jorge Ruesga
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiProcessSharedPreferencesProvider extends ContentProvider {

    private static final String AUTHORITY = "com.android.providers";

    private static final String PREFERENCES_ENTITY = "preferences";

    private static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    private static final int PREFERENCES_DATA = 1;
    private static final int PREFERENCES_DATA_ID = 2;

    private static final UriMatcher sURLMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sURLMatcher.addURI(AUTHORITY, PREFERENCES_ENTITY, PREFERENCES_DATA);
        sURLMatcher.addURI(AUTHORITY, PREFERENCES_ENTITY + "/*", PREFERENCES_DATA_ID);
    }

    private static final String FIELD_KEY = "key";
    private static final String FIELD_VALUE = "value";

    public static final String[] PROJECTION = {
        FIELD_KEY,
        FIELD_VALUE
    };


    private Context mContext;
    private SharedPreferences mPreferences;

    @Override
    @SuppressWarnings("ConstantConditions")
    public boolean onCreate() {
        mContext = getContext().getApplicationContext();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return false;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        int match = sURLMatcher.match(uri);
        switch (match) {
            case PREFERENCES_DATA:
                return "vnd.android.cursor.dir/" + PREFERENCES_ENTITY;
            case PREFERENCES_DATA_ID:
                return "vnd.android.cursor.item/" + PREFERENCES_ENTITY;
            default:
                throw new IllegalArgumentException("Unknown URL");
        }
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public Cursor query(@NonNull Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {

        MatrixCursor c = null;
        int match = sURLMatcher.match(uri);
        switch (match) {
            case PREFERENCES_DATA:
                Map<String, ?> map = mPreferences.getAll();
                c = new MatrixCursor(PROJECTION);
                for (String key : map.keySet()) {
                    MatrixCursor.RowBuilder row = c.newRow();
                    row.add(key);
                    Object val = map.get(key);
                    if (val instanceof Set<?>) {
                        row.add(marshallSet((Set<String>) val));
                    } else {
                        row.add(val);
                    }
                }
                break;

            case PREFERENCES_DATA_ID:
                final String key = uri.getPathSegments().get(1);
                map = mPreferences.getAll();
                if (map.containsKey(key)) {
                    c = new MatrixCursor(PROJECTION);
                    MatrixCursor.RowBuilder row = c.newRow();
                    row.add(key);
                    Object val = map.get(key);
                    if (val instanceof Set<?>) {
                        row.add(marshallSet((Set<String>) val));
                    } else {
                        row.add(val);
                    }
                }
                break;
        }

        if (c != null) {
            c.setNotificationUri(mContext.getContentResolver(), uri);
        }
        return c;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {

        String key;
        int match = sURLMatcher.match(uri);
        switch (match) {
            case PREFERENCES_DATA:
                SharedPreferences.Editor editor = mPreferences.edit();
                key = (String) values.get(FIELD_KEY);
                Object value = values.get(FIELD_VALUE);
                if(value != null) {
                    if (value instanceof Boolean) {
                        editor.putBoolean(key, (Boolean) value);
                    } else if (value instanceof Long) {
                        editor.putLong(key, (Long) value);
                    } else if (value instanceof Integer) {
                        editor.putInt(key, (Integer) value);
                    } else if (value instanceof Float) {
                        editor.putFloat(key, (Float) value);
                    } else {
                        // Test if the preference is a json array
                        try {
                            editor.putStringSet(key, unmarshallSet((String) value));
                        } catch (JSONException e) {
                            editor.putString(key, (String) value);
                        }
                    }
                } else {
                    editor.remove(key);
                }
                editor.apply();
                break;
            default:
                throw new IllegalArgumentException("Cannot insert from URL: " + uri);
        }

        // Notify
        Uri notifyUri = uri.buildUpon().appendEncodedPath(key).build();
        notifyChange(notifyUri);
        return notifyUri;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        switch (sURLMatcher.match(uri)) {
            case PREFERENCES_DATA:
                count = mPreferences.getAll().size();
                mPreferences.edit().clear().apply();
                break;
            case PREFERENCES_DATA_ID:
                final String key = uri.getPathSegments().get(1);
                if (mPreferences.contains(key)) {
                    mPreferences.edit().remove(key).apply();
                    count = 0;
                }
                break;
            default:
                throw new IllegalArgumentException("Cannot delete from URL: " + uri);
        }

        notifyChange(uri);
        return count;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values,
            String selection, String[] selectionArgs) {
        int count = 0;
        int match = sURLMatcher.match(uri);
        switch (match) {
            case PREFERENCES_DATA_ID:
                SharedPreferences.Editor editor = mPreferences.edit();
                final String key = uri.getPathSegments().get(1);
                Object value = values.get(FIELD_VALUE);
                if(value != null) {
                    if (value instanceof Boolean) {
                        editor.putBoolean(key, (Boolean) value);
                    } else if (value instanceof Long) {
                        editor.putLong(key, (Long) value);
                    } else if (value instanceof Integer) {
                        editor.putInt(key, (Integer) value);
                    } else if (value instanceof Float) {
                        editor.putFloat(key, (Float) value);
                    } else {
                        // Test if the preference is a json array
                        try {
                            editor.putStringSet(key, unmarshallSet((String) value));
                        } catch (JSONException e) {
                            editor.putString(key, (String) value);
                        }
                    }
                    count = 1;
                } else {
                    editor.remove(key);
                }
                editor.apply();
                break;
            default:
                throw new IllegalArgumentException("Cannot update from URL: " + uri);
        }

        notifyChange(uri);
        return count;
    }

    private void notifyChange(Uri uri) {
        mContext.getContentResolver().notifyChange(uri, null);
    }

    private static String marshallSet(Set<String> set) {
        JSONArray array = new JSONArray();
        for (String value : set) {
            array.put(value);
        }
        return array.toString();
    }

    private static Set<String> unmarshallSet(String value) throws JSONException {
        JSONArray array = new JSONArray(value);
        int size = array.length();
        Set<String> set = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            set.add(array.getString(i));
        }
        return set;
    }


    private static MultiProcessSharedPreferences sInstance;

    public static MultiProcessSharedPreferences getDefaultSharedPreferences(Context context) {
        if (sInstance == null) {
            sInstance = new MultiProcessSharedPreferences(context);
        }
        return sInstance;
    }

    public static class MultiProcessSharedPreferences implements SharedPreferences {

        public static class MultiProcessEditor implements Editor {

            private final Context mContext;
            private final List<Pair<String, Object>> mValues;

            public MultiProcessEditor(Context context) {
                mContext = context;
                mValues = new ArrayList<>();
            }

            @Override
            public Editor putString(String key, String value) {
                mValues.add(new Pair<>(key, (Object) value));
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                mValues.add(new Pair<>(key, (Object) values));
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                mValues.add(new Pair<>(key, (Object) value));
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                mValues.add(new Pair<>(key, (Object) value));
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                mValues.add(new Pair<>(key, (Object) value));
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                mValues.add(new Pair<>(key, (Object) value));
                return this;
            }

            @Override
            public Editor remove(String key) {
                Iterator<Pair<String, Object>> it = mValues.iterator();
                while (it.hasNext()) {
                    Pair<String, Object> v = it.next();
                    if (v.first.equals(key)) {
                        it.remove();
                        break;
                    }
                }
                return this;
            }

            @Override
            public Editor clear() {
                mValues.clear();
                return this;
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean commit() {
                Iterator<Pair<String, Object>> it = mValues.iterator();
                ContentValues values = new ContentValues();
                while (it.hasNext()) {
                    Pair<String, Object> v = it.next();
                    Uri uri = CONTENT_URI.buildUpon().appendPath(
                            PREFERENCES_ENTITY).appendPath(v.first).build();
                    values.put(FIELD_KEY, v.first);
                    if (v.second instanceof Boolean) {
                        values.put(FIELD_VALUE, (Boolean) v.second);
                    } else if (v.second instanceof Long) {
                        values.put(FIELD_VALUE, (Long) v.second);
                    } else if (v.second instanceof Integer) {
                        values.put(FIELD_VALUE, (Integer) v.second);
                    } else if (v.second instanceof Float) {
                        values.put(FIELD_VALUE, (Float) v.second);
                    } else if (v.second instanceof String) {
                        values.put(FIELD_VALUE, (String) v.second);
                    } else if (v.second instanceof Set) {
                        values.put(FIELD_VALUE, marshallSet((Set<String>) v.second));
                    } else {
                        throw new IllegalArgumentException();
                    }

                    mContext.getContentResolver().update(uri, values, null, null);
                }
                return true;
            }

            @Override
            public void apply() {
                commit();
            }
        }

        private final ContentObserver mObserver = new ContentObserver(new Handler()) {
            @Override
            public boolean deliverSelfNotifications() {
                return false;
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                String key = uri.getLastPathSegment();
                for (OnSharedPreferenceChangeListener cb : mListeners) {
                    cb.onSharedPreferenceChanged(MultiProcessSharedPreferences.this, key);
                }
            }
        };

        private final Context mContext;
        private final List<OnSharedPreferenceChangeListener> mListeners = new ArrayList<>();

        public MultiProcessSharedPreferences(Context context) {
            mContext = context;
            Uri uri = CONTENT_URI.buildUpon().appendPath(PREFERENCES_ENTITY).build();
            context.getContentResolver().registerContentObserver(uri, true, mObserver);
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            mContext.getContentResolver().unregisterContentObserver(mObserver);
        }

        @Override
        public Map<String, ?> getAll() {
            Map<String, Object> values = new HashMap<>();
            Uri uri = CONTENT_URI.buildUpon().appendPath(PREFERENCES_ENTITY).build();
            Cursor c = mContext.getContentResolver().query(uri, PROJECTION, null, null, null);
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        String key = c.getString(c.getColumnIndexOrThrow(FIELD_KEY));
                        int type = c.getType(c.getColumnIndexOrThrow(FIELD_VALUE));
                        Object value = null;
                        if (type == Cursor.FIELD_TYPE_INTEGER) {
                            value = c.getLong(c.getColumnIndexOrThrow(FIELD_VALUE));
                        } else if (type == Cursor.FIELD_TYPE_FLOAT) {
                            value = c.getFloat(c.getColumnIndexOrThrow(FIELD_VALUE));
                        } else if (type == Cursor.FIELD_TYPE_STRING) {
                            String v = c.getString(c.getColumnIndexOrThrow(FIELD_VALUE));
                            if (v.equals("true") || v.equals("false")) {
                                value = Boolean.parseBoolean(v);
                            } else {
                                try {
                                    value = unmarshallSet(v);
                                } catch (JSONException e) {
                                    value = v;
                                }
                            }
                        }

                        values.put(key, value);
                    }
                } finally {
                    c.close();
                }
            }
            return values;
        }

        @Nullable
        @Override
        public String getString(String key, String defValue) {
            Uri uri = CONTENT_URI.buildUpon().appendPath(
                    PREFERENCES_ENTITY).appendEncodedPath(key).build();
            Cursor c = mContext.getContentResolver().query(uri, PROJECTION, null, null, null);
            try {
                if (c == null || !c.moveToFirst()) {
                    return defValue;
                }
                int type = c.getType(c.getColumnIndexOrThrow(FIELD_VALUE));
                if (type != Cursor.FIELD_TYPE_STRING) {
                    return defValue;
                }

                return c.getString(c.getColumnIndexOrThrow(FIELD_VALUE));
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        @Nullable
        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Uri uri = CONTENT_URI.buildUpon().appendPath(
                    PREFERENCES_ENTITY).appendEncodedPath(key).build();
            Cursor c = mContext.getContentResolver().query(uri, PROJECTION, null, null, null);
            try {
                if (c == null || !c.moveToFirst()) {
                    return defValues;
                }
                int type = c.getType(c.getColumnIndexOrThrow(FIELD_VALUE));
                if (type != Cursor.FIELD_TYPE_STRING) {
                    return defValues;
                }

                try {
                    String v = c.getString(c.getColumnIndexOrThrow(FIELD_VALUE));
                    JSONArray array = new JSONArray(v);
                    int size = array.length();
                    Set<String> set = new HashSet<>(size);
                    for (int i = 0; i < size; i++) {
                        set.add(array.getString(i));
                    }
                    return set;

                } catch (JSONException e) {
                    // Ignore
                }

                return defValues;
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        @Override
        public int getInt(String key, int defValue) {
            Uri uri = CONTENT_URI.buildUpon().appendPath(
                    PREFERENCES_ENTITY).appendEncodedPath(key).build();
            Cursor c = mContext.getContentResolver().query(uri, PROJECTION, null, null, null);
            try {
                if (c == null || !c.moveToFirst()) {
                    return defValue;
                }
                int type = c.getType(c.getColumnIndexOrThrow(FIELD_VALUE));
                if (type != Cursor.FIELD_TYPE_INTEGER) {
                    return defValue;
                }

                return c.getInt(c.getColumnIndexOrThrow(FIELD_VALUE));
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        @Override
        public long getLong(String key, long defValue) {
            Uri uri = CONTENT_URI.buildUpon().appendPath(
                    PREFERENCES_ENTITY).appendEncodedPath(key).build();
            Cursor c = mContext.getContentResolver().query(uri, PROJECTION, null, null, null);
            try {
                if (c == null || !c.moveToFirst()) {
                    return defValue;
                }
                int type = c.getType(c.getColumnIndexOrThrow(FIELD_VALUE));
                if (type != Cursor.FIELD_TYPE_INTEGER) {
                    return defValue;
                }

                return c.getLong(c.getColumnIndexOrThrow(FIELD_VALUE));
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        @Override
        public float getFloat(String key, float defValue) {
            Uri uri = CONTENT_URI.buildUpon().appendPath(
                    PREFERENCES_ENTITY).appendEncodedPath(key).build();
            Cursor c = mContext.getContentResolver().query(uri, PROJECTION, null, null, null);
            try {
                if (c == null || !c.moveToFirst()) {
                    return defValue;
                }
                int type = c.getType(c.getColumnIndexOrThrow(FIELD_VALUE));
                if (type != Cursor.FIELD_TYPE_FLOAT) {
                    return defValue;
                }

                return c.getFloat(c.getColumnIndexOrThrow(FIELD_VALUE));
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Uri uri = CONTENT_URI.buildUpon().appendPath(
                    PREFERENCES_ENTITY).appendEncodedPath(key).build();
            Cursor c = mContext.getContentResolver().query(uri, PROJECTION, null, null, null);
            try {
                if (c == null || !c.moveToFirst()) {
                    return defValue;
                }
                int type = c.getType(c.getColumnIndexOrThrow(FIELD_VALUE));
                if (type != Cursor.FIELD_TYPE_STRING) {
                    return defValue;
                }

                return Boolean.parseBoolean(c.getString(c.getColumnIndexOrThrow(FIELD_VALUE)));
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        @Override
        public boolean contains(String key) {
            return getAll().containsKey(key);
        }

        @Override
        public Editor edit() {
            return new MultiProcessEditor(mContext);
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener cb) {
            mListeners.add(cb);
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener cb) {
            mListeners.remove(cb);
        }
    }
}
