package org.wordpress.android.fluxc.module;

import android.content.Context;

import org.wordpress.android.fluxc.persistence.BloggingRemindersDao;
import org.wordpress.android.fluxc.persistence.WPAndroidDatabase;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AppContextModule {
    private final Context mAppContext;

    public AppContextModule(Context appContext) {
        mAppContext = appContext;
    }

    @Singleton
    @Provides
    Context providesContext() {
        return mAppContext;
    }

    @Singleton
    @Provides
    WPAndroidDatabase provideDatabase(Context context){
        return WPAndroidDatabase.Companion.buildDb(context);
    }

    @Singleton
    @Provides
    BloggingRemindersDao provideBloggingRemindersDao(WPAndroidDatabase wpAndroidDatabase) {
        return wpAndroidDatabase.bloggingRemindersDao();
    }
}
