package org.cortex.terminal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

class CortexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocaleHelper.applySaved(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities.incrementAndGet()
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities.decrementAndGet()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

    }

    companion object {
        private val startedActivities = AtomicInteger(0)

        val isInForeground: Boolean
            get() = startedActivities.get() > 0
    }
}
