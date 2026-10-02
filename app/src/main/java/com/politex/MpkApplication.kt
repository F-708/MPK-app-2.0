package com.politex

import android.app.Application
import com.politex.data.local.MpkDatabase
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.StudentsRepository
import com.politex.data.repository.TeachersRepository
import com.politex.util.GroupCatalog
import com.politex.util.NotificationHelper

/**
 * E1/ARC-02/ARC-05: единый контейнер шаренных репозиториев.
 *
 * Раньше ScheduleRepository создавался в 6 местах с РАЗНЫМ набором зависимостей
 * (CollegeMapScreen — без teacherLessonDao: офлайн-преподаватели там молча не работали;
 * кэш cachedCatalog не шарился — ассет читался повторно). TeachersRepository создавался
 * на каждую композицию экрана. AppGraph даёт ОДИН экземпляр с ПОЛНЫМ набором deps.
 *
 * ponytail: object-синглтон вместо Hilt/Koin — DI-фреймворк на 66-файловое приложение
 * не окупается; если точек создания станет больше 10 — переезжать на Koin.
 */
object AppGraph {
    lateinit var scheduleRepository: ScheduleRepository
        private set
    lateinit var teachersRepository: TeachersRepository
        private set
    lateinit var studentsRepository: StudentsRepository
        private set

    fun init(context: Application) {
        val app = context.applicationContext
        val db = MpkDatabase.getInstance(app)
        scheduleRepository =
            ScheduleRepository(
                lessonDao = db.lessonDao(),
                catalogProvider = { GroupCatalog.load(app).all() },
                teacherLessonDao = db.teacherLessonDao(),
            )
        teachersRepository = TeachersRepository(app)
        studentsRepository = StudentsRepository(app)
    }
}

/**
 * E1/ARC-05: инициализация приложения раньше первой активити: процесс может быть поднят
 * воркером/ресивером/виджетом БЕЗ MainActivity — каналы уведомлений и AppGraph должны
 * существовать и там.
 */
class MpkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        NotificationHelper.createNotificationChannel(this)
        com.politex.util.BellCountdownNotifier
            .createChannel(this)
    }
}
