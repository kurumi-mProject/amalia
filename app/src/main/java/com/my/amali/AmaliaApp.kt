package com.my.amali

import android.app.Application
import com.my.amali.core.di.ServiceLocator

/**
 * Application-класс Амалии: единственная задача — инициализировать
 * DI-контейнер [ServiceLocator] как можно раньше в жизненном цикле процесса.
 *
 * Фонового прогрева движков здесь нет намеренно: единственный синтезатор
 * речи — облачный Fish Audio, у него нет локального сервиса, который нужно
 * поднимать заранее, а соединение всё равно открывается под конкретную фразу.
 */
class AmaliaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
