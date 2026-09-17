package com.my.amali

import android.app.Application
import com.my.amali.core.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-класс Амалии.
 *
 * Задачи две, и обе — про то, чтобы первый разговор начинался без пауз:
 *
 *  1. **Инициализировать DI-контейнер** [ServiceLocator] как можно раньше,
 *     пока Activity ещё создаётся.
 *  2. **Прогреть движки в фоне.** Системный синтез речи поднимает сервис
 *     за 1–2 секунды, а соединение с сервисами занимает время на TLS.
 *     Если прогрева нет, эта секунда добавляется к первому ответу — и
 *     пользователь слышит её как «задумалась» ровно тогда, когда
 *     ассистент впервые отвечает.
 *
 * Прогрев запускается в отдельной области ([SupervisorJob] + `Default`),
 * поэтому падение любого движка не влияет ни на UI, ни на процесс: это
 * необязательная оптимизация, а не условие работы.
 */
class AmaliaApp : Application() {

    /** Область для фоновых прогревов, живущая столько же, сколько процесс. */
    private val warmupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)

        // Прогрев не должен ничего ломать: движки умеют подниматься лениво,
        // а здесь мы просто даём им сделать это заранее.
        warmupScope.launch {
            runCatching { ServiceLocator.aiOrchestrator.initialize() }
        }
    }
}
