package com.my.amali

import android.app.Application
import com.my.amali.core.di.ServiceLocator

/**
 * Application-класс Амалии: единственная задача — инициализировать
 * DI-контейнер [ServiceLocator] как можно раньше в жизненном цикле процесса.
 */
class AmaliaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
