package com.anantmittal.cartbridge.di

import com.anantmittal.cartbridge.presentation.home_screen.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::HomeViewModel)
}
