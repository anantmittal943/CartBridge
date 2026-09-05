package com.anantmittal.cartbridge.di

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

fun initKoin(context: Context) {
    startKoin {
        androidLogger(Level.DEBUG)
        androidContext(context)
        modules(appModule)
    }
}
